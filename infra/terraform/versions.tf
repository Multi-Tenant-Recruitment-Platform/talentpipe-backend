terraform {
  # 1.11: write-only arguments, so generated passwords never reach the state.
  required_version = ">= 1.11.0"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 6.74"
    }
    random = {
      source  = "hashicorp/random"
      version = "~> 3.9"
    }
  }

  # Partial configuration: bucket, key and region come from backend.hcl
  # (copy backend.hcl.example), so no account detail is committed.
  # State locking uses a lock file in the bucket; no DynamoDB table needed.
  backend "s3" {}
}

provider "aws" {
  region = var.region

  default_tags {
    tags = local.tags
  }
}
