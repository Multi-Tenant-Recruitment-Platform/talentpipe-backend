# Running and deploying TalentPipe

You can run TalentPipe locally with Docker, or in production on AWS. The AWS setup is in `infra/terraform` and gets deployed from GitHub Actions. The reasons behind the production setup are in [ADR-16](DECISIONS.md#adr-16--production-runs-on-aws-behind-one-cloudfront-distribution).

## Local

You need Docker Desktop. For the full stack you also need `talentpipe-frontend` cloned next to this repo (or set `FRONTEND_DIR` to wherever you cloned it):

```
GitHub/
  talentpipe-backend/
  talentpipe-frontend/
```

Run the commands from `infra/`. Copy `.env.example` to `.env` if you want to change any defaults.

| What you want | Command | Open |
|---|---|---|
| Just the database (run the API from your IDE or `mvnw`) | `docker compose up -d postgres` | see the README |
| Database and API | `docker compose up -d --build` | http://localhost:8080/api/v1 |
| Everything, frontend included | `docker compose --profile full up -d --build` | http://localhost:5173 |

In the full stack the frontend's nginx serves the app and proxies `/api` to the backend, so it works the same way as production behind CloudFront. Email runs in console mode, so verification, invite and reset links show up in the backend log.

```bash
docker compose logs -f backend          # API log, including the emailed links
docker compose ps                       # container health
docker compose --profile full down      # stop (add -v to wipe the data too)
```

### Backend image

`backend/Dockerfile` builds from source, so you don't need a JDK installed.

- Two stages. The final image only has the JRE and the app, split into layers so a release usually only changes the top one.
- It runs as a non-root user (uid 10001) and can't modify its own files.
- The Docker health check calls `/actuator/health/liveness`.
- The heap gets 75% of the container's memory. On `OutOfMemoryError` the JVM exits and the container gets replaced.
- On `SIGTERM` it stops taking new requests and gives the ones in progress up to 20s to finish.

### Health endpoints

- `/actuator/health/liveness` checks that the process is up, nothing external. Docker and ECS use it.
- `/actuator/health/readiness` checks that the app can serve requests, including the database. The load balancer uses it.

Both work without a token and only return `UP` or `DOWN`. Nothing else under `/actuator` is exposed. In production CloudFront only forwards `/api/*`, so they aren't public anyway.

## Production on AWS

### Architecture

```
                      viewers (HTTPS only)
                              |
                 +------------v-------------+
                 |  CloudFront (global)     |   TLS, HTTP/2 and 3, security headers
                 |                          |
                 |  /api/*   ---------------+--> VPC origin ----+
                 |  /media/* --> S3 media   |   (logos, covers, |
                 |  /*       --> S3 SPA     |    photos only)   |
                 +--------------------------+                   |
                                                                |
  VPC 10.40.0.0/16, 2 Availability Zones                        |
  +-------------------------------------------------------------|-----+
  |  public subnets:  NAT gateway (outbound only)               |     |
  |                                                             v     |
  |  app subnets:     internal load balancer --> ECS Fargate tasks    |
  |                   (no public address)        backend, 2-4 tasks   |
  |                                                     |   |         |
  |  data subnets:    RDS PostgreSQL 15 <---------------+   +--> S3 media
  |                   (no route out of the VPC)               (gateway endpoint)
  +-------------------------------------------------------------------+

  Secrets Manager: DB password, JWT key, Resend key    ECR: backend images
  CloudWatch: logs, alarms -> SNS -> email             GitHub Actions: OIDC deploy roles
```

CloudFront is the only public part. The load balancer is internal and CloudFront reaches it through a VPC origin. The tasks and the database have no public IPs, and the database subnets have no route out of the VPC. Each security group only lets in traffic from the layer in front of it.

### What Terraform creates

| File | What's in it |
|---|---|
| `network.tf` | VPC, public/app/data subnets in 2 AZs, NAT gateway, S3 gateway endpoint, flow logs for rejected traffic |
| `security_groups.tf` | CloudFront to load balancer :80, to tasks :8080, to database :5432 |
| `alb.tf` | internal load balancer, target group checking `/actuator/health/readiness` |
| `ecs.tf` | cluster, task definition, service (rolling deploys with rollback), CPU autoscaling between 2 and 4 tasks |
| `rds.tf` | PostgreSQL 15 with pgvector, encrypted, TLS only, 7 days of backups, deletion protection |
| `s3.tf` | private SPA and media buckets (no public access, TLS only, versioned) |
| `cloudfront.tf` | the distribution, its three functions (`functions/`), the VPC origin and origin access control |
| `secrets.tf` | Secrets Manager entries. Values are generated during apply and never saved in the state |
| `ecr.tf` | image repo with immutable tags and scan on push; keeps the last 30 images |
| `iam.tf` | task roles, and the GitHub OIDC deploy roles for both repos |
| `monitoring.tf` | alarms for API 5xx, unhealthy tasks, p95 latency, task memory, database CPU and storage |

### Cost

Roughly US$110-140 a month at low traffic in `ap-south-1` with the defaults. That's an estimate, so check the [AWS Pricing Calculator](https://calculator.aws/) before relying on it.

Most of it is the NAT gateway (about 35%), Fargate (about 25%, two 0.5 vCPU / 1 GiB tasks on Graviton), the load balancer (about 20%) and RDS (about 15%, `db.t4g.micro` with 20 GiB). CloudFront, Secrets Manager, CloudWatch and S3 make up the rest and grow with traffic.

The settings that change the bill the most are `db_multi_az` (adds a standby database, roughly doubling its cost), `single_nat_gateway = false` (one NAT gateway per AZ) and `backend_min_count`.

### First-time setup

You'll need an AWS account with admin credentials set up in the AWS CLI, Terraform 1.11 or newer (or just Docker, see [Checks](#checks)), and admin access to both GitHub repos.

**1. Create the Terraform state bucket.** Only once per account.

```bash
cd infra/terraform/bootstrap
terraform init
terraform apply
terraform output -raw backend_config > ../backend.hcl
```

**2. Create the environment.** This takes 20-30 minutes, mostly waiting on CloudFront and the VPC origin.

```bash
cd infra/terraform
cp prod.tfvars.example prod.tfvars        # set alarm_email, and your domain if you have one
terraform init -backend-config=backend.hcl
terraform plan -var-file=prod.tfvars -out=prod.tfplan
terraform apply prod.tfplan
terraform output
```

The ECS tasks keep failing until the first image is deployed in step 4. That's expected.

**3. Set up GitHub.** In both repos, create an environment called `production` (Settings > Environments). Restrict it to the `master` branch, and add required reviewers if you want deploys to wait for approval.

Then add these repository variables (Settings > Secrets and variables > Actions > Variables). None of them are secret. AWS access goes through OIDC, so there are no AWS keys in GitHub.

| Repo | Variable | `terraform output` |
|---|---|---|
| both | `AWS_REGION` | `aws_region` |
| both | `SITE_URL` | `site_url` |
| talentpipe-backend | `AWS_DEPLOY_ROLE_ARN` | `backend_deploy_role_arn` |
| talentpipe-backend | `ECR_REPOSITORY_URL` | `ecr_repository_url` |
| talentpipe-backend | `ECS_CLUSTER` | `ecs_cluster` |
| talentpipe-backend | `ECS_SERVICE` | `ecs_service` |
| talentpipe-backend | `ECS_TASK_FAMILY` | `ecs_task_family` |
| talentpipe-frontend | `AWS_DEPLOY_ROLE_ARN` | `frontend_deploy_role_arn` |
| talentpipe-frontend | `SPA_BUCKET` | `spa_bucket` |
| talentpipe-frontend | `CLOUDFRONT_DISTRIBUTION_ID` | `cloudfront_distribution_id` |

**4. First deploy.** In each repo go to Actions, pick `backend-deploy` / `frontend-deploy` and run it on `master`. After that every push to `master` deploys automatically. The deploy workflows run the tests themselves before shipping anything. They don't wait for CI to finish because CI also runs for pull requests from forks, and the deploy should only run code that's in the repo.

**5. Check the client IP.** The login rate limit is per IP, and it takes the IP from the `X-Viewer-Ip` header that a CloudFront function adds (`functions/viewer-ip.js`). To check it works, try to sign in 11 times within a minute with an email that has no account. The 11th attempt should get a 429. Then look at the log:

```bash
aws logs tail /ecs/talentpipe-prod/backend --since 10m | grep "Rate limit"
```

It should show your public IP. If it shows a `10.40.x.x` address, everyone is sharing one limit, so check that the `/api/*` behaviour still uses the AllViewerExceptHostHeader origin request policy.

### Custom domain

1. Request a certificate in **us-east-1** for the name (e.g. `app.example.com`) and validate it. CloudFront only uses certificates from that region.
2. Set `domain_aliases = ["app.example.com"]` and `acm_certificate_arn` in `prod.tfvars`, then apply.
3. Point the name at `cloudfront_domain_name`, with a CNAME or a Route 53 alias record.
4. Update `SITE_URL` in both repos and redeploy the backend. Email links and allowed origins use the first alias.

### Email

By default email is in console mode: links go to the log and nothing gets sent. To send real email through Resend, add the key:

```bash
aws secretsmanager put-secret-value \
  --secret-id talentpipe/prod/resend-api-key \
  --secret-string 're_...'
```

Then set `email_enabled = true` and a `mail_from` address on a domain you've verified in Resend (the sandbox sender only delivers to the Resend account owner). Apply, then redeploy the backend. The key itself never goes through Terraform.

## Day to day

### Deploying and rolling back

- **Release:** merge to `master`. `backend-deploy` and `frontend-deploy` test the commit and ship it.
- **Redeploy** (for example after a Terraform change to the backend settings): run the deploy workflow on `master`.
- **Roll back:** run the deploy workflow with the last good commit SHA. Images are tagged by commit, so nothing gets rebuilt.

The backend rollout replaces tasks one at a time, and each new task has to pass the readiness check first. If the new tasks never get healthy, ECS goes back to the previous version and the workflow fails.

### Migrations

Each task runs Flyway on startup, and a Postgres lock stops two tasks migrating at once. During a rollout the old and new versions both run against the new schema for a few minutes, so a migration must not break the previous release:

- Add a column or table in one release and start using it in the next.
- To rename or drop something, stop using it first and drop it in a later release.
- Don't edit a `V*.sql` that has already run. Production has `FLYWAY_REPAIR=false`, so a changed checksum stops the app from starting instead of being quietly accepted.

### Logs and alarms

```bash
aws logs tail /ecs/talentpipe-prod/backend --follow                 # app
aws logs tail /aws/rds/instance/talentpipe-prod/postgresql --follow # database (queries over 1s)
aws logs tail /vpc/talentpipe-prod/rejected                         # connections the network refused
```

Alarms go to the SNS topic `talentpipe-prod-alarms`. If you set `alarm_email`, AWS sends a confirmation email to that address first.

### Rotating secrets

Bump the version in `prod.tfvars`, apply, then run `backend-deploy` so the tasks pick up the new value.

- `db_password_version` sets a new database password. RDS switches within a minute, and new DB connections from the old tasks fail after that, so deploy right away.
- `jwt_secret_version` sets a new JWT signing key. Access tokens signed with the old key stop working.

### Getting a shell in a task

Set `enable_execute_command = true`, apply, then:

```bash
aws ecs execute-command --cluster talentpipe-prod --task <task-id> --container backend --interactive --command sh
```

### Tearing it down

1. Set `deletion_protection = false` and apply.
2. Empty both buckets, including old versions (`aws s3 rm s3://<bucket> --recursive` doesn't remove those).
3. Run `terraform destroy`. RDS takes a final snapshot called `talentpipe-prod-final` first.

## Checks

The `infra` job in CI runs these on every PR. None of them need an AWS account.

```bash
cd infra/terraform
terraform fmt -check -recursive
terraform init -backend=false && terraform validate
terraform test                                     # whole config against a mocked AWS provider
node --test functions/functions.test.js            # the CloudFront functions
trivy config --severity MEDIUM,HIGH,CRITICAL .     # security misconfigurations
hadolint ../../backend/Dockerfile
```

The Terraform tests (`tests/production.tftest.hcl`) check that nothing besides CloudFront is reachable from the internet, the database is private and only accepts TLS, no secret is a plain environment variable, CVs can't be read through CloudFront, the API is never cached, everything is HTTPS only, and only each repo's `production` environment can deploy.

If you don't have Terraform installed, run it through Docker:

```bash
docker run --rm -v "$PWD:/w" -w /w hashicorp/terraform:1.17.2 test
```

On the app side, `DeploymentReadinessIntegrationTest` and `SecurityConfigWebTest` cover the health endpoints, and check that the login rate limit counts by the IP from CloudFront and ignores a forged `X-Forwarded-For`.
