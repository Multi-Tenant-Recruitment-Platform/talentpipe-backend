# Internal: it has no public address, and the only way in is CloudFront's VPC
# origin. Plain HTTP is therefore private traffic inside the VPC; TLS ends at
# CloudFront, in front of every viewer.

#trivy:ignore:AVD-AWS-0053 -- internal by design, reached only through the CloudFront VPC origin
resource "aws_lb" "main" {
  name               = "${local.name}-api"
  internal           = true
  load_balancer_type = "application"
  security_groups    = [aws_security_group.alb.id]
  subnets            = aws_subnet.app[*].id

  # Requests with malformed headers are dropped, not passed to the backend.
  drop_invalid_header_fields = true
  # Longer than CloudFront's 60 s origin timeout, so CloudFront, not the load
  # balancer, decides when a slow request has failed.
  idle_timeout               = 75
  enable_deletion_protection = var.deletion_protection

  tags = { Name = "${local.name}-api" }
}

resource "aws_lb_target_group" "backend" {
  name        = "${local.name}-backend"
  vpc_id      = aws_vpc.main.id
  target_type = "ip"
  protocol    = "HTTP"
  port        = local.backend_port

  # Requests in flight get this long to finish when a task is replaced. The
  # application's graceful shutdown (20 s) fits inside the task's 30 s stop
  # timeout.
  deregistration_delay = 30

  # Readiness includes the database: a task that cannot reach it gets no
  # traffic. Liveness (the container's own check) does not, so the same outage
  # never makes ECS restart the tasks.
  health_check {
    path                = "/actuator/health/readiness"
    matcher             = "200"
    interval            = 15
    timeout             = 5
    healthy_threshold   = 2
    unhealthy_threshold = 3
  }

  tags = { Name = "${local.name}-backend" }
}

#trivy:ignore:AVD-AWS-0054 -- private hop inside the VPC; viewers only ever speak HTTPS to CloudFront
resource "aws_lb_listener" "http" {
  load_balancer_arn = aws_lb.main.arn
  port              = 80
  protocol          = "HTTP"

  default_action {
    type             = "forward"
    target_group_arn = aws_lb_target_group.backend.arn
  }
}
