# Four roles, each with only what its holder needs:
#
#   task execution   ECS itself, starting a task: pull the image, read the
#                    task's secrets, write its logs
#   task             the running backend: read and write the media bucket
#   backend deploy   the backend repository's deployment job: push an image,
#                    register a revision, update the service
#   frontend deploy  the frontend repository's deployment job: upload the
#                    built site, invalidate CloudFront's copy

data "aws_iam_policy_document" "ecs_tasks_assume" {
  statement {
    actions = ["sts:AssumeRole"]

    principals {
      type        = "Service"
      identifiers = ["ecs-tasks.amazonaws.com"]
    }

    # Only tasks of this account can use the roles.
    condition {
      test     = "StringEquals"
      variable = "aws:SourceAccount"
      values   = [local.account_id]
    }
  }
}

# --- Task execution ----------------------------------------------------------------

resource "aws_iam_role" "task_execution" {
  name               = "${local.name}-backend-execution"
  assume_role_policy = data.aws_iam_policy_document.ecs_tasks_assume.json
}

resource "aws_iam_role_policy_attachment" "task_execution_managed" {
  role       = aws_iam_role.task_execution.name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AmazonECSTaskExecutionRolePolicy"
}

data "aws_iam_policy_document" "task_execution_secrets" {
  statement {
    sid     = "ReadTheTasksSecrets"
    actions = ["secretsmanager:GetSecretValue"]
    resources = [
      aws_secretsmanager_secret.db_password.arn,
      aws_secretsmanager_secret.jwt.arn,
      aws_secretsmanager_secret.resend_api_key.arn,
    ]
  }
}

resource "aws_iam_role_policy" "task_execution_secrets" {
  name   = "read-secrets"
  role   = aws_iam_role.task_execution.id
  policy = data.aws_iam_policy_document.task_execution_secrets.json
}

# --- Task (the running backend) -------------------------------------------------------

resource "aws_iam_role" "task" {
  name               = "${local.name}-backend-task"
  assume_role_policy = data.aws_iam_policy_document.ecs_tasks_assume.json
}

data "aws_iam_policy_document" "task" {
  statement {
    sid       = "MediaObjects"
    actions   = ["s3:PutObject", "s3:GetObject", "s3:DeleteObject"]
    resources = [for prefix in local.backend_media_prefixes : "${aws_s3_bucket.this["media"].arn}/${prefix}"]
  }

  # `aws ecs execute-command` into a running task, when enabled.
  dynamic "statement" {
    for_each = var.enable_execute_command ? [1] : []

    content {
      sid = "EcsExec"
      actions = [
        "ssmmessages:CreateControlChannel",
        "ssmmessages:CreateDataChannel",
        "ssmmessages:OpenControlChannel",
        "ssmmessages:OpenDataChannel",
      ]
      resources = ["*"]
    }
  }
}

resource "aws_iam_role_policy" "task" {
  name   = "backend"
  role   = aws_iam_role.task.id
  policy = data.aws_iam_policy_document.task.json
}

# --- GitHub Actions (OpenID Connect: no stored AWS keys) ----------------------------

resource "aws_iam_openid_connect_provider" "github" {
  count = var.create_github_oidc_provider ? 1 : 0

  url            = "https://token.actions.githubusercontent.com"
  client_id_list = ["sts.amazonaws.com"]
}

data "aws_iam_openid_connect_provider" "github" {
  count = var.create_github_oidc_provider ? 0 : 1

  url = "https://token.actions.githubusercontent.com"
}

locals {
  github_oidc_provider_arn = var.create_github_oidc_provider ? aws_iam_openid_connect_provider.github[0].arn : data.aws_iam_openid_connect_provider.github[0].arn
}

# Only a job of the named repository, running in the GitHub environment, can
# assume the role: not a fork, not another branch's job outside the
# environment, not another repository.
data "aws_iam_policy_document" "github_assume" {
  for_each = {
    backend  = var.backend_repository
    frontend = var.frontend_repository
  }

  statement {
    actions = ["sts:AssumeRoleWithWebIdentity"]

    principals {
      type        = "Federated"
      identifiers = [local.github_oidc_provider_arn]
    }

    condition {
      test     = "StringEquals"
      variable = "token.actions.githubusercontent.com:aud"
      values   = ["sts.amazonaws.com"]
    }

    condition {
      test     = "StringEquals"
      variable = "token.actions.githubusercontent.com:sub"
      values   = ["repo:${var.github_org}/${each.value}:environment:${var.github_environment}"]
    }
  }
}

resource "aws_iam_role" "backend_deploy" {
  name                 = "${local.name}-backend-deploy"
  assume_role_policy   = data.aws_iam_policy_document.github_assume["backend"].json
  max_session_duration = 3600
}

data "aws_iam_policy_document" "backend_deploy" {
  statement {
    sid       = "EcrLogin"
    actions   = ["ecr:GetAuthorizationToken"]
    resources = ["*"]
  }

  statement {
    sid = "EcrPush"
    actions = [
      "ecr:BatchCheckLayerAvailability",
      "ecr:BatchGetImage",
      "ecr:CompleteLayerUpload",
      "ecr:DescribeImages",
      "ecr:GetDownloadUrlForLayer",
      "ecr:InitiateLayerUpload",
      "ecr:PutImage",
      "ecr:UploadLayerPart",
    ]
    resources = [aws_ecr_repository.backend.arn]
  }

  # Neither action supports resource-level permissions.
  statement {
    sid       = "TaskDefinitions"
    actions   = ["ecs:DescribeTaskDefinition", "ecs:RegisterTaskDefinition"]
    resources = ["*"]
  }

  statement {
    sid       = "DeployTheService"
    actions   = ["ecs:UpdateService", "ecs:DescribeServices"]
    resources = [aws_ecs_service.backend.id]
  }

  statement {
    sid       = "HandTheTaskItsRoles"
    actions   = ["iam:PassRole"]
    resources = [aws_iam_role.task_execution.arn, aws_iam_role.task.arn]

    condition {
      test     = "StringEquals"
      variable = "iam:PassedToService"
      values   = ["ecs-tasks.amazonaws.com"]
    }
  }
}

resource "aws_iam_role_policy" "backend_deploy" {
  name   = "deploy"
  role   = aws_iam_role.backend_deploy.id
  policy = data.aws_iam_policy_document.backend_deploy.json
}

resource "aws_iam_role" "frontend_deploy" {
  name                 = "${local.name}-frontend-deploy"
  assume_role_policy   = data.aws_iam_policy_document.github_assume["frontend"].json
  max_session_duration = 3600
}

data "aws_iam_policy_document" "frontend_deploy" {
  statement {
    sid       = "ListTheSite"
    actions   = ["s3:ListBucket"]
    resources = [aws_s3_bucket.this["spa"].arn]
  }

  statement {
    sid       = "WriteTheSite"
    actions   = ["s3:PutObject", "s3:GetObject", "s3:DeleteObject"]
    resources = ["${aws_s3_bucket.this["spa"].arn}/*"]
  }

  statement {
    sid       = "RefreshTheCache"
    actions   = ["cloudfront:CreateInvalidation", "cloudfront:GetInvalidation"]
    resources = [aws_cloudfront_distribution.main.arn]
  }
}

resource "aws_iam_role_policy" "frontend_deploy" {
  name   = "deploy"
  role   = aws_iam_role.frontend_deploy.id
  policy = data.aws_iam_policy_document.frontend_deploy.json
}
