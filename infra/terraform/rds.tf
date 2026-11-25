resource "aws_db_subnet_group" "main" {
  name       = local.name
  subnet_ids = aws_subnet.data[*].id
}

resource "aws_db_parameter_group" "main" {
  name   = "${local.name}-pg15"
  family = "postgres15"

  # Unencrypted connections are refused. (PostgreSQL 15 on RDS allows them by
  # default.)
  parameter {
    name  = "rds.force_ssl"
    value = "1"
  }

  # Statements slower than a second go to the log (CloudWatch, below).
  parameter {
    name  = "log_min_duration_statement"
    value = "1000"
  }
}

# PostgreSQL 15, as everywhere else (docker-compose, Testcontainers). pgvector
# is available on RDS, and V1__extensions.sql creates it: the master user has
# rds_superuser.
#trivy:ignore:AVD-AWS-0133 -- Performance Insights is not available on the smallest instance classes; enable it with a larger class
#trivy:ignore:AVD-AWS-0176 -- password authentication over TLS; IAM authentication would need code changes in the backend
resource "aws_db_instance" "main" {
  identifier = local.name

  engine         = "postgres"
  engine_version = "15"
  instance_class = var.db_instance_class

  db_name  = "talentpipe"
  username = "talentpipe"
  # Write-only: the password is in neither the plan nor the state.
  password_wo         = ephemeral.random_password.db.result
  password_wo_version = var.db_password_version

  storage_type          = "gp3"
  allocated_storage     = var.db_allocated_storage
  max_allocated_storage = var.db_max_allocated_storage
  storage_encrypted     = true

  db_subnet_group_name   = aws_db_subnet_group.main.name
  vpc_security_group_ids = [aws_security_group.db.id]
  parameter_group_name   = aws_db_parameter_group.main.name
  publicly_accessible    = false
  multi_az               = var.db_multi_az

  # Times in UTC: 23:30-00:30 and Sunday 01:00-02:00 in Sri Lanka.
  backup_retention_period = var.db_backup_retention_days
  backup_window           = "18:00-19:00"
  maintenance_window      = "sun:19:30-sun:20:30"
  copy_tags_to_snapshot   = true

  auto_minor_version_upgrade      = true
  enabled_cloudwatch_logs_exports = ["postgresql"]

  deletion_protection       = var.deletion_protection
  skip_final_snapshot       = false
  final_snapshot_identifier = "${local.name}-final"

  # Changes wait for the maintenance window unless applied by hand.
  apply_immediately = false

  tags = { Name = local.name }

  depends_on = [aws_cloudwatch_log_group.postgres]
}

# RDS would create this group itself on first export, keeping logs forever.
# Creating it first sets the retention.
resource "aws_cloudwatch_log_group" "postgres" {
  name              = "/aws/rds/instance/${local.name}/postgresql"
  retention_in_days = var.log_retention_days
}
