# Two private buckets. Neither is public: CloudFront reads them with origin
# access control (cloudfront.tf), and each bucket policy names the one
# distribution allowed to.
#
#   spa    the built frontend, written by the frontend's deployment workflow
#   media  logos, covers, profile photos and CVs, written by the backend.
#          CloudFront may read logos, covers and photos. CVs are never
#          served by CloudFront: the API hands them only to someone allowed
#          to see them.

locals {
  buckets = {
    spa   = "${local.name}-spa-${local.account_id}"
    media = "${local.name}-media-${local.account_id}"
  }

  # Object keys as the backend writes them (FileStorageService):
  #   tenant/<tenantId>/logo/<uuid>.<ext>, tenant/<tenantId>/cover/...
  #   candidate/<candidateId>/photos/...,  candidate/<candidateId>/cvs/...
  public_media_prefixes  = ["tenant/*/logo/*", "tenant/*/cover/*", "candidate/*/photos/*"]
  backend_media_prefixes = ["tenant/*", "candidate/*"]
}

#trivy:ignore:AVD-AWS-0089 -- access logging is left to CloudFront and CloudTrail
resource "aws_s3_bucket" "this" {
  for_each = local.buckets

  bucket = each.value
}

resource "aws_s3_bucket_public_access_block" "this" {
  for_each = aws_s3_bucket.this

  bucket                  = each.value.id
  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

# ACLs off: access is decided by policies alone.
resource "aws_s3_bucket_ownership_controls" "this" {
  for_each = aws_s3_bucket.this

  bucket = each.value.id

  rule {
    object_ownership = "BucketOwnerEnforced"
  }
}

#trivy:ignore:AVD-AWS-0132 -- SSE-S3; a customer key would also need granting to CloudFront, for no gain here
resource "aws_s3_bucket_server_side_encryption_configuration" "this" {
  for_each = aws_s3_bucket.this

  bucket = each.value.id

  rule {
    apply_server_side_encryption_by_default {
      sse_algorithm = "AES256"
    }
  }
}

# Overwritten or deleted objects stay recoverable for 30 days: a bad SPA
# deployment can be rolled back, an accidentally deleted CV restored.
resource "aws_s3_bucket_versioning" "this" {
  for_each = aws_s3_bucket.this

  bucket = each.value.id

  versioning_configuration {
    status = "Enabled"
  }
}

resource "aws_s3_bucket_lifecycle_configuration" "this" {
  for_each = aws_s3_bucket.this

  bucket = each.value.id

  rule {
    id     = "expire-old-versions"
    status = "Enabled"

    filter {}

    noncurrent_version_expiration {
      noncurrent_days = 30
    }

    abort_incomplete_multipart_upload {
      days_after_initiation = 7
    }
  }

  depends_on = [aws_s3_bucket_versioning.this]
}

# --- Bucket policies -----------------------------------------------------------------

data "aws_iam_policy_document" "tls_only" {
  for_each = aws_s3_bucket.this

  statement {
    sid     = "DenyInsecureTransport"
    effect  = "Deny"
    actions = ["s3:*"]
    resources = [
      each.value.arn,
      "${each.value.arn}/*",
    ]

    principals {
      type        = "*"
      identifiers = ["*"]
    }

    condition {
      test     = "Bool"
      variable = "aws:SecureTransport"
      values   = ["false"]
    }
  }
}

data "aws_iam_policy_document" "spa" {
  source_policy_documents = [data.aws_iam_policy_document.tls_only["spa"].json]

  statement {
    sid       = "CloudFrontReadsTheSite"
    actions   = ["s3:GetObject"]
    resources = ["${aws_s3_bucket.this["spa"].arn}/*"]

    principals {
      type        = "Service"
      identifiers = ["cloudfront.amazonaws.com"]
    }

    condition {
      test     = "StringEquals"
      variable = "AWS:SourceArn"
      values   = [aws_cloudfront_distribution.main.arn]
    }
  }
}

data "aws_iam_policy_document" "media" {
  source_policy_documents = [data.aws_iam_policy_document.tls_only["media"].json]

  statement {
    sid       = "CloudFrontReadsPublicMediaOnly"
    actions   = ["s3:GetObject"]
    resources = [for prefix in local.public_media_prefixes : "${aws_s3_bucket.this["media"].arn}/${prefix}"]

    principals {
      type        = "Service"
      identifiers = ["cloudfront.amazonaws.com"]
    }

    condition {
      test     = "StringEquals"
      variable = "AWS:SourceArn"
      values   = [aws_cloudfront_distribution.main.arn]
    }
  }
}

resource "aws_s3_bucket_policy" "spa" {
  bucket = aws_s3_bucket.this["spa"].id
  policy = data.aws_iam_policy_document.spa.json

  depends_on = [aws_s3_bucket_public_access_block.this]
}

resource "aws_s3_bucket_policy" "media" {
  bucket = aws_s3_bucket.this["media"].id
  policy = data.aws_iam_policy_document.media.json

  depends_on = [aws_s3_bucket_public_access_block.this]
}
