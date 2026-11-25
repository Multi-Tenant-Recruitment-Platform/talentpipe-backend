output "site_url" {
  description = "Where the platform answers."
  value       = local.public_url
}

output "cloudfront_domain_name" {
  description = "Point the custom domain's DNS (CNAME, or an alias record in Route 53) here."
  value       = aws_cloudfront_distribution.main.domain_name
}

# --- For the GitHub repositories' deployment settings (docs/DEPLOYMENT.md) ------

output "aws_region" {
  description = "GitHub variable AWS_REGION, in both repositories."
  value       = var.region
}

output "backend_deploy_role_arn" {
  description = "GitHub variable AWS_DEPLOY_ROLE_ARN in talentpipe-backend."
  value       = aws_iam_role.backend_deploy.arn
}

output "ecr_repository_url" {
  description = "GitHub variable ECR_REPOSITORY_URL in talentpipe-backend."
  value       = aws_ecr_repository.backend.repository_url
}

output "ecs_cluster" {
  description = "GitHub variable ECS_CLUSTER in talentpipe-backend."
  value       = aws_ecs_cluster.main.name
}

output "ecs_service" {
  description = "GitHub variable ECS_SERVICE in talentpipe-backend."
  value       = aws_ecs_service.backend.name
}

output "ecs_task_family" {
  description = "GitHub variable ECS_TASK_FAMILY in talentpipe-backend."
  value       = aws_ecs_task_definition.backend.family
}

output "frontend_deploy_role_arn" {
  description = "GitHub variable AWS_DEPLOY_ROLE_ARN in talentpipe-frontend."
  value       = aws_iam_role.frontend_deploy.arn
}

output "spa_bucket" {
  description = "GitHub variable SPA_BUCKET in talentpipe-frontend."
  value       = aws_s3_bucket.this["spa"].id
}

output "cloudfront_distribution_id" {
  description = "GitHub variable CLOUDFRONT_DISTRIBUTION_ID in talentpipe-frontend."
  value       = aws_cloudfront_distribution.main.id
}

# --- Operations ---------------------------------------------------------------------

output "media_bucket" {
  description = "Uploaded logos, covers, photos and CVs."
  value       = aws_s3_bucket.this["media"].id
}

output "backend_log_group" {
  description = "Backend logs: aws logs tail <this> --follow"
  value       = aws_cloudwatch_log_group.backend.name
}

output "database_endpoint" {
  description = "Database host, reachable only from inside the VPC."
  value       = aws_db_instance.main.address
}

output "secrets" {
  description = "Secrets Manager names. resend_api_key is filled by hand (docs/DEPLOYMENT.md)."
  value = {
    db_password    = aws_secretsmanager_secret.db_password.name
    jwt_secret     = aws_secretsmanager_secret.jwt.name
    resend_api_key = aws_secretsmanager_secret.resend_api_key.name
  }
}

output "alarm_topic_arn" {
  description = "SNS topic the alarms notify; subscribe more endpoints to it if needed."
  value       = aws_sns_topic.alarms.arn
}
