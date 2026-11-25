# The backend on Fargate: no servers to patch, tasks in private subnets,
# behind the internal load balancer.
#
# Who owns what: Terraform owns the cluster, the service and the task
# definition's settings (environment, secrets, sizes). The deployment workflow
# (.github/workflows/deploy.yml) owns the image. It takes the newest revision
# of this family, swaps in the image it just built and registers that as the
# next revision. So the service's task_definition is left to it
# (ignore_changes below), and a change made here reaches the tasks at the next
# deployment.

resource "aws_ecs_cluster" "main" {
  name = local.name

  setting {
    name  = "containerInsights"
    value = var.container_insights ? "enabled" : "disabled"
  }
}

#trivy:ignore:AVD-AWS-0017 -- encrypted at rest by CloudWatch Logs; a customer key adds cost, not protection, here
resource "aws_cloudwatch_log_group" "backend" {
  name              = "/ecs/${local.name}/backend"
  retention_in_days = var.log_retention_days
}

resource "aws_ecs_task_definition" "backend" {
  family                   = "${local.name}-backend"
  requires_compatibilities = ["FARGATE"]
  network_mode             = "awsvpc"
  cpu                      = var.backend_cpu
  memory                   = var.backend_memory
  execution_role_arn       = aws_iam_role.task_execution.arn
  task_role_arn            = aws_iam_role.task.arn

  runtime_platform {
    operating_system_family = "LINUX"
    cpu_architecture        = var.backend_cpu_architecture
  }

  # The root filesystem is read-only; this is the one writable place (Tomcat's
  # work directory, multipart uploads in progress, JVM temporary files).
  volume {
    name = "tmp"
  }

  container_definitions = jsonencode([
    {
      name      = local.backend_container
      image     = "${aws_ecr_repository.backend.repository_url}:${var.backend_image_tag}"
      essential = true

      portMappings = [{ containerPort = local.backend_port, protocol = "tcp" }]

      readonlyRootFilesystem = true
      mountPoints            = [{ sourceVolume = "tmp", containerPath = "/tmp", readOnly = false }]
      linuxParameters        = { initProcessEnabled = true }
      # SIGTERM, then this long before SIGKILL. The application drains for up
      # to 20 s (spring.lifecycle.timeout-per-shutdown-phase).
      stopTimeout = 30

      environment = [
        { name = "SERVER_PORT", value = tostring(local.backend_port) },
        { name = "DB_URL", value = "jdbc:postgresql://${aws_db_instance.main.address}:${aws_db_instance.main.port}/${aws_db_instance.main.db_name}?sslmode=require" },
        { name = "DB_USERNAME", value = aws_db_instance.main.username },
        # Never rewrite a migration's checksum in production.
        { name = "FLYWAY_REPAIR", value = "false" },
        { name = "FRONTEND_BASE_URL", value = local.public_url },
        { name = "CORS_ALLOWED_ORIGINS", value = join(",", local.site_origins) },
        { name = "MAIL_FROM", value = var.mail_from },
        { name = "STORAGE_PROVIDER", value = "s3" },
        { name = "AWS_S3_BUCKET", value = aws_s3_bucket.this["media"].id },
        { name = "AWS_S3_REGION", value = var.region },
        { name = "AWS_CDN_BASE_URL", value = "${local.public_url}/media" },
        # Client address for the login rate limit: from the header CloudFront's
        # function sets, honoured only on requests from inside the VPC.
        { name = "FORWARD_HEADERS_STRATEGY", value = "native" },
        { name = "CLIENT_IP_HEADER", value = local.viewer_ip_header },
      ]

      # Read by ECS from Secrets Manager when the task starts.
      secrets = concat(
        [
          { name = "DB_PASSWORD", valueFrom = aws_secretsmanager_secret.db_password.arn },
          { name = "JWT_SECRET", valueFrom = aws_secretsmanager_secret.jwt.arn },
        ],
        var.email_enabled ? [{ name = "RESEND_API_KEY", valueFrom = aws_secretsmanager_secret.resend_api_key.arn }] : [],
      )

      # Liveness: should the process be restarted? Readiness, which also checks
      # the database, is the load balancer's (alb.tf).
      healthCheck = {
        command     = ["CMD-SHELL", "curl -fsS http://127.0.0.1:${local.backend_port}/actuator/health/liveness > /dev/null || exit 1"]
        interval    = 15
        timeout     = 5
        retries     = 3
        startPeriod = 120
      }

      logConfiguration = {
        logDriver = "awslogs"
        options = {
          awslogs-group         = aws_cloudwatch_log_group.backend.name
          awslogs-region        = var.region
          awslogs-stream-prefix = local.backend_container
          # A slow log service must never block the application.
          mode            = "non-blocking"
          max-buffer-size = "25m"
        }
      }
    }
  ])

  # Older revisions stay registered, so a rollback can always go back to one.
  skip_destroy = true

  # The secret versions must exist before a task can start with them.
  depends_on = [
    aws_secretsmanager_secret_version.db_password,
    aws_secretsmanager_secret_version.jwt,
  ]
}

resource "aws_ecs_service" "backend" {
  name             = "backend"
  cluster          = aws_ecs_cluster.main.id
  task_definition  = aws_ecs_task_definition.backend.arn
  desired_count    = var.backend_min_count
  launch_type      = "FARGATE"
  platform_version = "LATEST"

  network_configuration {
    subnets          = aws_subnet.app[*].id
    security_groups  = [aws_security_group.backend.id]
    assign_public_ip = false
  }

  load_balancer {
    target_group_arn = aws_lb_target_group.backend.arn
    container_name   = local.backend_container
    container_port   = local.backend_port
  }

  # Start-up runs Flyway's migrations before the first health check can pass.
  health_check_grace_period_seconds = 180

  # Rolling deployment: new tasks start and turn healthy before old ones stop.
  # One that never does is rolled back automatically.
  deployment_minimum_healthy_percent = 100
  deployment_maximum_percent         = 200

  deployment_circuit_breaker {
    enable   = true
    rollback = true
  }

  availability_zone_rebalancing = "ENABLED"
  enable_execute_command        = var.enable_execute_command
  propagate_tags                = "SERVICE"

  lifecycle {
    # task_definition: the deployment workflow's. desired_count: autoscaling's.
    ignore_changes = [task_definition, desired_count]
  }

  depends_on = [aws_lb_listener.http]
}

# --- Autoscaling -------------------------------------------------------------------

resource "aws_appautoscaling_target" "backend" {
  service_namespace  = "ecs"
  resource_id        = "service/${aws_ecs_cluster.main.name}/${aws_ecs_service.backend.name}"
  scalable_dimension = "ecs:service:DesiredCount"
  min_capacity       = var.backend_min_count
  max_capacity       = var.backend_max_count
}

resource "aws_appautoscaling_policy" "backend_cpu" {
  name               = "${local.name}-backend-cpu"
  policy_type        = "TargetTrackingScaling"
  service_namespace  = aws_appautoscaling_target.backend.service_namespace
  resource_id        = aws_appautoscaling_target.backend.resource_id
  scalable_dimension = aws_appautoscaling_target.backend.scalable_dimension

  target_tracking_scaling_policy_configuration {
    target_value       = 60
    scale_out_cooldown = 60
    scale_in_cooldown  = 300

    predefined_metric_specification {
      predefined_metric_type = "ECSServiceAverageCPUUtilization"
    }
  }
}
