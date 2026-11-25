# One distribution, one origin for the browser:
#
#   /api/*     backend, through the internal load balancer (VPC origin)
#   /media/*   public images from the media bucket
#   /*         the single-page app from the SPA bucket
#
# Same origin for page and API means no third-party cookies, no preflight
# for most calls, and the frontend calls the relative /api/v1, exactly as it
# does behind nginx locally.

data "aws_cloudfront_cache_policy" "caching_optimized" {
  name = "Managed-CachingOptimized"
}

data "aws_cloudfront_cache_policy" "caching_disabled" {
  name = "Managed-CachingDisabled"
}

# Every viewer header (Authorization, X-Tenant-Subdomain, ...) but Host, which
# stays the load balancer's own name.
data "aws_cloudfront_origin_request_policy" "all_viewer_except_host" {
  name = "Managed-AllViewerExceptHostHeader"
}

# HSTS, nosniff, frame-options SAMEORIGIN, referrer policy.
data "aws_cloudfront_response_headers_policy" "security_headers" {
  name = "Managed-SecurityHeadersPolicy"
}

resource "aws_cloudfront_origin_access_control" "s3" {
  name                              = "${local.name}-s3"
  description                       = "CloudFront reads the private SPA and media buckets"
  origin_access_control_origin_type = "s3"
  signing_behavior                  = "always"
  signing_protocol                  = "sigv4"
}

# CloudFront's way into the VPC: it places its own network interfaces in the
# private subnets, so the load balancer needs no public address.
resource "aws_cloudfront_vpc_origin" "api" {
  vpc_origin_endpoint_config {
    name                   = "${local.name}-api"
    arn                    = aws_lb.main.arn
    http_port              = 80
    https_port             = 443
    origin_protocol_policy = "http-only"

    origin_ssl_protocols {
      items    = ["TLSv1.2"]
      quantity = 1
    }
  }

  timeouts {
    create = "30m"
    update = "30m"
    delete = "30m"
  }
}

resource "aws_cloudfront_function" "spa_router" {
  name    = "${local.name}-spa-router"
  comment = "Serve index.html for routes of the single-page app"
  runtime = "cloudfront-js-2.0"
  code    = file("${path.module}/functions/spa-router.js")
  publish = true
}

resource "aws_cloudfront_function" "media_router" {
  name    = "${local.name}-media-router"
  comment = "Map /media/<key> to public objects of the media bucket; 404 for anything else"
  runtime = "cloudfront-js-2.0"
  code    = file("${path.module}/functions/media-router.js")
  publish = true
}

resource "aws_cloudfront_function" "viewer_ip" {
  name    = "${local.name}-viewer-ip"
  comment = "Tell the backend the viewer's address in ${local.viewer_ip_header}"
  runtime = "cloudfront-js-2.0"
  code    = file("${path.module}/functions/viewer-ip.js")
  publish = true
}

#trivy:ignore:AVD-AWS-0010 -- standard logging is optional and billed; enable it when needed
#trivy:ignore:AVD-AWS-0011 -- a WAF web ACL can be attached with var.web_acl_arn
#trivy:ignore:AVD-AWS-0013 -- the *.cloudfront.net certificate fixes the minimum version; with a custom domain it is TLSv1.2_2021
resource "aws_cloudfront_distribution" "main" {
  enabled             = true
  comment             = "${local.name}: SPA, API and media"
  aliases             = var.domain_aliases
  default_root_object = "index.html"
  http_version        = "http2and3"
  is_ipv6_enabled     = true
  price_class         = var.cloudfront_price_class
  web_acl_id          = var.web_acl_arn

  origin {
    origin_id                = "spa"
    domain_name              = aws_s3_bucket.this["spa"].bucket_regional_domain_name
    origin_access_control_id = aws_cloudfront_origin_access_control.s3.id
  }

  origin {
    origin_id                = "media"
    domain_name              = aws_s3_bucket.this["media"].bucket_regional_domain_name
    origin_access_control_id = aws_cloudfront_origin_access_control.s3.id
  }

  origin {
    origin_id   = "api"
    domain_name = aws_lb.main.dns_name

    vpc_origin_config {
      vpc_origin_id = aws_cloudfront_vpc_origin.api.id
      # The most CloudFront allows without a quota increase. Invitation and
      # password-reset requests send their email inline.
      origin_read_timeout      = 60
      origin_keepalive_timeout = 5
    }
  }

  # The single-page app. index.html is uploaded with Cache-Control: no-cache
  # and the content-hashed assets as immutable (frontend deployment workflow),
  # and CachingOptimized follows those headers.
  default_cache_behavior {
    target_origin_id           = "spa"
    viewer_protocol_policy     = "redirect-to-https"
    allowed_methods            = ["GET", "HEAD"]
    cached_methods             = ["GET", "HEAD"]
    compress                   = true
    cache_policy_id            = data.aws_cloudfront_cache_policy.caching_optimized.id
    response_headers_policy_id = data.aws_cloudfront_response_headers_policy.security_headers.id

    function_association {
      event_type   = "viewer-request"
      function_arn = aws_cloudfront_function.spa_router.arn
    }
  }

  # The API: never cached, every method, every header but Host.
  ordered_cache_behavior {
    path_pattern               = "/api/*"
    target_origin_id           = "api"
    viewer_protocol_policy     = "redirect-to-https"
    allowed_methods            = ["DELETE", "GET", "HEAD", "OPTIONS", "PATCH", "POST", "PUT"]
    cached_methods             = ["GET", "HEAD"]
    compress                   = true
    cache_policy_id            = data.aws_cloudfront_cache_policy.caching_disabled.id
    origin_request_policy_id   = data.aws_cloudfront_origin_request_policy.all_viewer_except_host.id
    response_headers_policy_id = data.aws_cloudfront_response_headers_policy.security_headers.id

    function_association {
      event_type   = "viewer-request"
      function_arn = aws_cloudfront_function.viewer_ip.arn
    }
  }

  # Logos, covers and photos. Every upload gets a new random key, so an
  # object never changes once it exists and can be cached for long.
  ordered_cache_behavior {
    path_pattern               = "/media/*"
    target_origin_id           = "media"
    viewer_protocol_policy     = "redirect-to-https"
    allowed_methods            = ["GET", "HEAD"]
    cached_methods             = ["GET", "HEAD"]
    compress                   = true
    cache_policy_id            = data.aws_cloudfront_cache_policy.caching_optimized.id
    response_headers_policy_id = data.aws_cloudfront_response_headers_policy.security_headers.id

    function_association {
      event_type   = "viewer-request"
      function_arn = aws_cloudfront_function.media_router.arn
    }
  }

  restrictions {
    geo_restriction {
      restriction_type = "none"
    }
  }

  viewer_certificate {
    cloudfront_default_certificate = var.acm_certificate_arn == null
    acm_certificate_arn            = var.acm_certificate_arn
    ssl_support_method             = var.acm_certificate_arn == null ? null : "sni-only"
    minimum_protocol_version       = var.acm_certificate_arn == null ? "TLSv1" : "TLSv1.2_2021"
  }
}
