# --- Placement -----------------------------------------------------------------

variable "region" {
  description = "AWS Region for everything except CloudFront, which is global."
  type        = string
  default     = "ap-south-1"
}

variable "project" {
  description = "Prefix for resource names."
  type        = string
  default     = "talentpipe"
}

variable "environment" {
  description = "Environment name, part of every resource name (prod, staging, ...)."
  type        = string
  default     = "prod"

  validation {
    condition     = can(regex("^[a-z][a-z0-9-]{1,15}$", var.environment))
    error_message = "Use 2-16 lower-case letters, digits or hyphens, starting with a letter."
  }
}

# --- Network ---------------------------------------------------------------------

variable "vpc_cidr" {
  description = "Address range of the VPC. Subnets are /24s carved from it."
  type        = string
  default     = "10.40.0.0/16"
}

variable "az_count" {
  description = "Availability Zones to spread across. RDS and the load balancer need at least 2."
  type        = number
  default     = 2

  validation {
    condition     = var.az_count >= 2 && var.az_count <= 3
    error_message = "Use 2 or 3 Availability Zones."
  }
}

variable "single_nat_gateway" {
  description = "One NAT gateway for all zones (cheaper), or one per zone (outbound traffic survives the loss of a zone)."
  type        = bool
  default     = true
}

# --- Public entry point ----------------------------------------------------------

variable "domain_aliases" {
  description = "Custom domain names for the site, e.g. [\"app.talentpipe.lk\"]. Empty: use the *.cloudfront.net name."
  type        = list(string)
  default     = []
}

variable "acm_certificate_arn" {
  description = "ACM certificate in us-east-1 covering every name in domain_aliases. Required when domain_aliases is set."
  type        = string
  default     = null

  validation {
    condition     = length(var.domain_aliases) == 0 || var.acm_certificate_arn != null
    error_message = "domain_aliases needs acm_certificate_arn: a certificate in us-east-1 that covers those names."
  }
}

variable "cloudfront_price_class" {
  description = "PriceClass_100 (North America, Europe), PriceClass_200 (adds Asia incl. India, Middle East, Africa) or PriceClass_All."
  type        = string
  default     = "PriceClass_200"
}

variable "web_acl_arn" {
  description = "Optional AWS WAF web ACL (CLOUDFRONT scope, created in us-east-1) to attach to the distribution."
  type        = string
  default     = null
}

# --- Backend service -------------------------------------------------------------

variable "backend_image_tag" {
  description = "Image tag Terraform's own task definition revision uses. Deployments (GitHub Actions) register later revisions with the commit's tag; this one only matters until the first deployment."
  type        = string
  default     = "bootstrap"
}

variable "backend_cpu" {
  description = "Fargate CPU units per task (1024 = 1 vCPU)."
  type        = number
  default     = 512
}

variable "backend_memory" {
  description = "Fargate memory per task, MiB. The JVM heap takes 75% of it."
  type        = number
  default     = 1024
}

variable "backend_cpu_architecture" {
  description = "ARM64 (Graviton, about 20% cheaper) or X86_64. The deployment workflow builds the image for whichever is set here."
  type        = string
  default     = "ARM64"

  validation {
    condition     = contains(["ARM64", "X86_64"], var.backend_cpu_architecture)
    error_message = "Use ARM64 or X86_64."
  }
}

variable "backend_min_count" {
  description = "Fewest backend tasks. 2 keeps the API up through a deployment or the loss of a zone."
  type        = number
  default     = 2
}

variable "backend_max_count" {
  description = "Most backend tasks the CPU-based autoscaling may run."
  type        = number
  default     = 4
}

variable "enable_execute_command" {
  description = "Allow `aws ecs execute-command` into running tasks, for debugging."
  type        = bool
  default     = false
}

variable "container_insights" {
  description = "CloudWatch Container Insights for the cluster (per-task metrics; billed as custom metrics)."
  type        = bool
  default     = false
}

variable "log_retention_days" {
  description = "How long CloudWatch keeps application and database logs."
  type        = number
  default     = 30
}

# --- Email -----------------------------------------------------------------------

variable "email_enabled" {
  description = "Send real email through Resend. Put the API key in the <project>/<environment>/resend-api-key secret BEFORE turning this on (see docs/DEPLOYMENT.md). Off: links are written to the log."
  type        = bool
  default     = false
}

variable "mail_from" {
  description = "Sender address. Must be on a domain verified in Resend to reach anyone but the Resend account owner."
  type        = string
  default     = "onboarding@resend.dev"
}

# --- Database ----------------------------------------------------------------------

variable "db_instance_class" {
  description = "RDS instance class."
  type        = string
  default     = "db.t4g.micro"
}

variable "db_allocated_storage" {
  description = "Initial database storage, GiB."
  type        = number
  default     = 20
}

variable "db_max_allocated_storage" {
  description = "Storage autoscaling ceiling, GiB."
  type        = number
  default     = 100
}

variable "db_multi_az" {
  description = "A standby in a second zone with automatic failover. Roughly doubles the database cost."
  type        = bool
  default     = false
}

variable "db_backup_retention_days" {
  description = "Days of automated backups (point-in-time recovery window)."
  type        = number
  default     = 7
}

variable "deletion_protection" {
  description = "Refuse to delete the database and the load balancer. Turn off, apply, then destroy, to tear the environment down."
  type        = bool
  default     = true
}

# --- Secret rotation ---------------------------------------------------------------
# Secrets are generated during apply and never stored in the state. Raising a
# version generates a new value and writes it everywhere it is used; then run
# the deployment workflow so the tasks pick it up (docs/DEPLOYMENT.md).

variable "db_password_version" {
  description = "Raise by one to generate a new database password."
  type        = number
  default     = 1
}

variable "jwt_secret_version" {
  description = "Raise by one to generate a new JWT signing secret. Every session ends: users sign in again."
  type        = number
  default     = 1
}

# --- Monitoring --------------------------------------------------------------------

variable "alarm_email" {
  description = "Address that receives alarm notifications. It must confirm the subscription email from AWS. Null: alarms are created without a subscriber."
  type        = string
  default     = null
}

# --- CI/CD (GitHub Actions, OpenID Connect) -----------------------------------------

variable "github_org" {
  description = "GitHub organization that owns the repositories."
  type        = string
  default     = "Multi-Tenant-Recruitment-Platform"
}

variable "backend_repository" {
  description = "Repository whose workflow deploys the backend."
  type        = string
  default     = "talentpipe-backend"
}

variable "frontend_repository" {
  description = "Repository whose workflow deploys the SPA."
  type        = string
  default     = "talentpipe-frontend"
}

variable "github_environment" {
  description = "GitHub environment the deployment jobs run in. Only jobs in it can assume the deploy roles; give it required reviewers to gate production."
  type        = string
  default     = "production"
}

variable "create_github_oidc_provider" {
  description = "Create the account's GitHub OIDC provider. False if the account already has one (there can be only one per URL)."
  type        = bool
  default     = true
}
