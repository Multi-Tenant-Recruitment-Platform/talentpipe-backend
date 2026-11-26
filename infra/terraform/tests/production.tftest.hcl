# Offline tests of the production configuration: a mocked AWS provider, so
# no account, no credentials and no cost. They run the whole configuration
# (references, counts, conditionals, generated JSON) and pin the properties
# that keep production safe.
#
#   terraform init -backend=false && terraform test

mock_provider "aws" {
  override_data {
    target = data.aws_availability_zones.available
    values = { names = ["ap-south-1a", "ap-south-1b", "ap-south-1c"] }
  }

  override_data {
    target = data.aws_caller_identity.current
    values = { account_id = "123456789012" }
  }

  # Real-looking ARNs wherever the provider validates them.
  mock_resource "aws_s3_bucket" {
    defaults = { arn = "arn:aws:s3:::mock-bucket" }
  }

  mock_resource "aws_lb" {
    defaults = {
      arn        = "arn:aws:elasticloadbalancing:ap-south-1:123456789012:loadbalancer/app/mock/0123456789abcdef"
      arn_suffix = "app/mock/0123456789abcdef"
      dns_name   = "internal-mock-123456.ap-south-1.elb.amazonaws.com"
    }
  }

  mock_resource "aws_lb_target_group" {
    defaults = {
      arn        = "arn:aws:elasticloadbalancing:ap-south-1:123456789012:targetgroup/mock/0123456789abcdef"
      arn_suffix = "targetgroup/mock/0123456789abcdef"
    }
  }

  mock_resource "aws_sns_topic" {
    defaults = { arn = "arn:aws:sns:ap-south-1:123456789012:mock" }
  }

  mock_resource "aws_iam_role" {
    defaults = { arn = "arn:aws:iam::123456789012:role/mock" }
  }

  mock_resource "aws_iam_openid_connect_provider" {
    defaults = { arn = "arn:aws:iam::123456789012:oidc-provider/token.actions.githubusercontent.com" }
  }

  mock_resource "aws_secretsmanager_secret" {
    defaults = { arn = "arn:aws:secretsmanager:ap-south-1:123456789012:secret:mock-AbCdEf" }
  }

  mock_resource "aws_ecr_repository" {
    defaults = {
      arn            = "arn:aws:ecr:ap-south-1:123456789012:repository/mock"
      repository_url = "123456789012.dkr.ecr.ap-south-1.amazonaws.com/mock"
    }
  }

  mock_resource "aws_cloudwatch_log_group" {
    defaults = { arn = "arn:aws:logs:ap-south-1:123456789012:log-group:mock" }
  }

  mock_resource "aws_cloudfront_function" {
    defaults = { arn = "arn:aws:cloudfront::123456789012:function/mock" }
  }

  mock_resource "aws_ecs_cluster" {
    defaults = { arn = "arn:aws:ecs:ap-south-1:123456789012:cluster/mock" }
  }

  mock_resource "aws_ecs_service" {
    defaults = { id = "arn:aws:ecs:ap-south-1:123456789012:service/mock/backend" }
  }

  mock_resource "aws_ecs_task_definition" {
    defaults = { arn = "arn:aws:ecs:ap-south-1:123456789012:task-definition/mock:1" }
  }

  mock_resource "aws_cloudfront_distribution" {
    defaults = {
      arn         = "arn:aws:cloudfront::123456789012:distribution/EMOCK"
      domain_name = "d111111abcdef8.cloudfront.net"
    }
  }

  mock_data "aws_iam_policy_document" {
    defaults = { json = "{}" }
  }
}

run "defaults" {
  command = apply

  # --- Network: nothing reachable from the internet ---------------------------------

  assert {
    condition     = length(aws_subnet.app) == 2 && length(aws_subnet.data) == 2 && length(aws_subnet.public) == 2
    error_message = "Two zones, three tiers."
  }

  assert {
    condition     = length(aws_nat_gateway.main) == 1
    error_message = "One NAT gateway unless single_nat_gateway is false."
  }

  assert {
    condition     = length(aws_route_table.data.route) == 0
    error_message = "The database subnets must have no route out of the VPC."
  }

  assert {
    condition     = aws_lb.main.internal && aws_lb.main.drop_invalid_header_fields
    error_message = "The load balancer must be internal and drop malformed headers."
  }

  assert {
    condition     = aws_vpc_security_group_ingress_rule.alb_from_cloudfront.prefix_list_id == data.aws_ec2_managed_prefix_list.cloudfront_origin_facing.id
    error_message = "Only CloudFront may reach the load balancer."
  }

  assert {
    condition     = aws_ecs_service.backend.network_configuration[0].assign_public_ip == false
    error_message = "Backend tasks must not get public addresses."
  }

  # --- Database ----------------------------------------------------------------------------

  assert {
    condition = (
      aws_db_instance.main.publicly_accessible == false
      && aws_db_instance.main.storage_encrypted
      && aws_db_instance.main.deletion_protection
      && aws_db_instance.main.skip_final_snapshot == false
    )
    error_message = "The database must be private, encrypted, protected from deletion, and snapshot on delete."
  }

  assert {
    condition     = one([for p in aws_db_parameter_group.main.parameter : p.value if p.name == "rds.force_ssl"]) == "1"
    error_message = "The database must refuse unencrypted connections."
  }

  assert {
    condition     = aws_db_instance.main.password == null && aws_db_instance.main.password_wo_version == 1
    error_message = "The database password must be write-only, never in the state."
  }

  # --- Backend task ------------------------------------------------------------------------

  assert {
    condition = alltrue([
      for pair in [
        ["FORWARD_HEADERS_STRATEGY", "native"],
        ["CLIENT_IP_HEADER", "X-Viewer-Ip"],
        ["FLYWAY_REPAIR", "false"],
        ["STORAGE_PROVIDER", "s3"],
        ["FRONTEND_BASE_URL", "https://d111111abcdef8.cloudfront.net"],
        ["AWS_CDN_BASE_URL", "https://d111111abcdef8.cloudfront.net/media"],
      ] : contains([for e in jsondecode(aws_ecs_task_definition.backend.container_definitions)[0].environment : "${e.name}=${e.value}"], "${pair[0]}=${pair[1]}")
    ])
    error_message = "The backend's production settings are wrong."
  }

  assert {
    condition     = endswith(one([for e in jsondecode(aws_ecs_task_definition.backend.container_definitions)[0].environment : e.value if e.name == "DB_URL"]), "?sslmode=require")
    error_message = "The backend must connect to the database over TLS."
  }

  assert {
    condition     = sort([for s in jsondecode(aws_ecs_task_definition.backend.container_definitions)[0].secrets : s.name]) == tolist(["DB_PASSWORD", "JWT_SECRET"])
    error_message = "Secrets come from Secrets Manager; no Resend key while email is off."
  }

  assert {
    condition = alltrue([
      for e in jsondecode(aws_ecs_task_definition.backend.container_definitions)[0].environment :
      !contains(["DB_PASSWORD", "JWT_SECRET", "RESEND_API_KEY"], e.name)
    ])
    error_message = "No secret may be a plain environment variable."
  }

  assert {
    condition     = jsondecode(aws_ecs_task_definition.backend.container_definitions)[0].readonlyRootFilesystem
    error_message = "The container's root filesystem must be read-only."
  }

  assert {
    condition     = aws_ecs_service.backend.deployment_circuit_breaker[0].rollback && aws_ecs_service.backend.deployment_minimum_healthy_percent == 100
    error_message = "Deployments must be rolling and roll back on failure."
  }

  # --- Media: CVs are never public -------------------------------------------------------

  assert {
    condition = alltrue([
      for r in one([for s in data.aws_iam_policy_document.media.statement : s.resources if s.sid == "CloudFrontReadsPublicMediaOnly"]) :
      !strcontains(r, "cvs") && !endswith(r, "candidate/*")
    ])
    error_message = "CloudFront must not be able to read CVs."
  }

  assert {
    condition     = alltrue([for b in aws_s3_bucket_public_access_block.this : b.block_public_acls && b.block_public_policy && b.ignore_public_acls && b.restrict_public_buckets])
    error_message = "Both buckets must block every form of public access."
  }

  # --- Edge ---------------------------------------------------------------------------------

  assert {
    condition = one([
      for b in aws_cloudfront_distribution.main.ordered_cache_behavior : b.cache_policy_id if b.path_pattern == "/api/*"
    ]) == data.aws_cloudfront_cache_policy.caching_disabled.id
    error_message = "API responses must never be cached."
  }

  assert {
    condition = alltrue(concat(
      [aws_cloudfront_distribution.main.default_cache_behavior[0].viewer_protocol_policy == "redirect-to-https"],
      [for b in aws_cloudfront_distribution.main.ordered_cache_behavior : b.viewer_protocol_policy == "redirect-to-https"],
    ))
    error_message = "Every path must be HTTPS only."
  }

  # --- CI/CD: only the right repository, in the protected environment ---------------------

  assert {
    condition = one(flatten([
      for c in data.aws_iam_policy_document.github_assume["backend"].statement[0].condition : c.values if c.variable == "token.actions.githubusercontent.com:sub"
    ])) == "repo:Multi-Tenant-Recruitment-Platform/talentpipe-backend:environment:production"
    error_message = "Only the backend repository's production environment may deploy the backend."
  }

  assert {
    condition = one(flatten([
      for c in data.aws_iam_policy_document.github_assume["frontend"].statement[0].condition : c.values if c.variable == "token.actions.githubusercontent.com:sub"
    ])) == "repo:Multi-Tenant-Recruitment-Platform/talentpipe-frontend:environment:production"
    error_message = "Only the frontend repository's production environment may deploy the SPA."
  }
}

run "custom_domain_email_and_high_availability" {
  command = apply

  variables {
    domain_aliases      = ["app.example.com"]
    acm_certificate_arn = "arn:aws:acm:us-east-1:123456789012:certificate/0000"
    email_enabled       = true
    single_nat_gateway  = false
    alarm_email         = "ops@example.com"
  }

  assert {
    condition     = length(aws_nat_gateway.main) == 2 && aws_route_table.app[0].route != aws_route_table.app[1].route
    error_message = "One NAT gateway per zone, each zone routed through its own."
  }

  assert {
    condition     = contains([for s in jsondecode(aws_ecs_task_definition.backend.container_definitions)[0].secrets : s.name], "RESEND_API_KEY")
    error_message = "With email on, the task reads the Resend key from Secrets Manager."
  }

  assert {
    condition = one([
      for e in jsondecode(aws_ecs_task_definition.backend.container_definitions)[0].environment : e.value if e.name == "CORS_ALLOWED_ORIGINS"
    ]) == "https://app.example.com,https://d111111abcdef8.cloudfront.net"
    error_message = "The API must accept the page from every address the site answers on."
  }

  assert {
    condition     = one([for e in jsondecode(aws_ecs_task_definition.backend.container_definitions)[0].environment : e.value if e.name == "FRONTEND_BASE_URL"]) == "https://app.example.com"
    error_message = "Email links must use the custom domain."
  }

  assert {
    condition     = aws_cloudfront_distribution.main.viewer_certificate[0].minimum_protocol_version == "TLSv1.2_2021"
    error_message = "A custom domain must require TLS 1.2 or later."
  }

  assert {
    condition     = length(aws_sns_topic_subscription.alarm_email) == 1
    error_message = "The alarm address must be subscribed."
  }
}

run "a_custom_domain_needs_a_certificate" {
  command = plan

  variables {
    domain_aliases = ["app.example.com"]
  }

  expect_failures = [var.acm_certificate_arn]
}
