# Generated during apply, written where they are used, and never stored:
# ephemeral values exist only while Terraform runs, and write-only arguments
# leave nothing in the state or the plan. A version variable decides when a
# new value is written (variables.tf, "Secret rotation").
#
# The backend reads them as environment variables that ECS fills from Secrets
# Manager when a task starts (ecs.tf).

ephemeral "random_password" "db" {
  # Letters and digits only: RDS rejects some symbols in master passwords, and
  # 40 alphanumerics are ~238 bits, far beyond guessing.
  length  = 40
  special = false
}

ephemeral "random_password" "jwt" {
  # HS256 needs at least 32 bytes; this is 64.
  length  = 64
  special = false
}

#trivy:ignore:AVD-AWS-0098 -- encrypted with the AWS-managed key; a customer key would add cost, not protection, here
resource "aws_secretsmanager_secret" "db_password" {
  name                    = "${var.project}/${var.environment}/db-password"
  description             = "Master password of the TalentPipe database (DB_PASSWORD)"
  recovery_window_in_days = 7
}

# Same ephemeral value as aws_db_instance.main.password_wo, in the same run,
# because both follow var.db_password_version.
resource "aws_secretsmanager_secret_version" "db_password" {
  secret_id                = aws_secretsmanager_secret.db_password.id
  secret_string_wo         = ephemeral.random_password.db.result
  secret_string_wo_version = var.db_password_version
}

#trivy:ignore:AVD-AWS-0098 -- encrypted with the AWS-managed key
resource "aws_secretsmanager_secret" "jwt" {
  name                    = "${var.project}/${var.environment}/jwt-secret"
  description             = "HS256 signing key for access tokens (JWT_SECRET)"
  recovery_window_in_days = 7
}

resource "aws_secretsmanager_secret_version" "jwt" {
  secret_id                = aws_secretsmanager_secret.jwt.id
  secret_string_wo         = ephemeral.random_password.jwt.result
  secret_string_wo_version = var.jwt_secret_version
}

# Created empty. The key is put in by hand (docs/DEPLOYMENT.md), so it never
# passes through Terraform at all, and only then is email_enabled turned on.
#trivy:ignore:AVD-AWS-0098 -- encrypted with the AWS-managed key
resource "aws_secretsmanager_secret" "resend_api_key" {
  name                    = "${var.project}/${var.environment}/resend-api-key"
  description             = "Resend API key (RESEND_API_KEY); read only when email_enabled is true"
  recovery_window_in_days = 7
}
