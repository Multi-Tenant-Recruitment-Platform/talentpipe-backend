data "aws_caller_identity" "current" {}

data "aws_availability_zones" "available" {
  state = "available"
}

locals {
  name       = "${var.project}-${var.environment}"
  account_id = data.aws_caller_identity.current.account_id
  azs        = slice(data.aws_availability_zones.available.names, 0, var.az_count)

  tags = {
    Project     = var.project
    Environment = var.environment
    ManagedBy   = "terraform"
  }

  # The address people use: the first custom domain, or CloudFront's own.
  public_host = length(var.domain_aliases) > 0 ? var.domain_aliases[0] : aws_cloudfront_distribution.main.domain_name
  public_url  = "https://${local.public_host}"

  # Every address the site answers on. The API sees the page's origin as
  # cross-origin (CloudFront reaches it over HTTP under another host name),
  # so each one has to be allowed.
  site_origins = concat(
    [for alias in var.domain_aliases : "https://${alias}"],
    ["https://${aws_cloudfront_distribution.main.domain_name}"],
  )

  backend_container = "backend"
  backend_port      = 8080

  # Header the CloudFront function sets to the viewer's address; the backend
  # takes the client address from it (CLIENT_IP_HEADER).
  viewer_ip_header = "X-Viewer-Ip"
}
