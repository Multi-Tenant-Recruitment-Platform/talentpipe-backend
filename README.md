# TalentPipe — Backend

[![backend-ci](https://github.com/RavinduIT/talentpipe-backend/actions/workflows/ci.yml/badge.svg?branch=development)](https://github.com/RavinduIT/talentpipe-backend/actions/workflows/ci.yml)

Multi-tenant recruitment platform: one deployment hosts many companies, each with
isolated jobs, candidates and hiring pipelines. This repository contains the
Spring Boot API, database migrations and local infrastructure.

The React SPA lives in a separate repository: **talentpipe-frontend**.

## Tech stack

| Piece | Tech |
|---|---|
| Runtime | Java 17, Spring Boot 3.x (modular monolith) |
| Persistence | Spring Data JPA / Hibernate, Flyway, PostgreSQL 15 (+ pgvector) |
| Security | Spring Security, JJWT (stateless), bcrypt |
| Quality | JUnit 5, Testcontainers, JaCoCo, SonarQube/SonarCloud |
| Infra | Docker Compose (`infra/`), GitHub Actions CI |

Modules live under `com.talentpipe.<module>` (`auth`, `tenant`, `candidate`,
`job`, `notification`, `pipeline`, `ai`, `analytics`) plus the shared `common`
kernel and `security` infrastructure. Architectural ground rules and rationale:
[docs/DECISIONS.md](docs/DECISIONS.md).

## Repository layout

```
backend/          Spring Boot application, Flyway migrations, Dockerfile
infra/            docker-compose.yml (local stack) + .env.example
infra/terraform/  production on AWS (CloudFront, ECS Fargate, RDS, S3)
docs/             API reference, architecture decisions, deployment, quality setup
```

## Branching & contributing

| Branch | Purpose | Rules |
|---|---|---|
| `master` | Production-ready | PR only · **2 approvals** · CI green |
| `development` | Integration | PR only · **1 approval** · CI green |
| `feature/*` | All work | branch off `development` |

See [CONTRIBUTING.md](CONTRIBUTING.md).

## Quick start

Prerequisites: **JDK 17**, **Docker Desktop** (Maven is not needed — the repo
ships the Maven wrapper).

```bash
# 1. Database
cd infra && cp .env.example .env
docker compose up -d postgres

# 2. API (requires a JWT secret ≥ 32 bytes)
cd ../backend
$env:JWT_SECRET = "dev-only-secret-at-least-32-bytes-long-change-me"   # PowerShell
# export JWT_SECRET="dev-only-secret-at-least-32-bytes-long-change-me" # bash
./mvnw spring-boot:run          # Windows: .\mvnw.cmd spring-boot:run
```

API: `http://localhost:8080/api/v1`. Flyway migrates the schema on first boot.

To run everything (database, API and frontend) in Docker:

```bash
cd infra
docker compose --profile full up -d --build     # then open http://localhost:5173
```

This needs `talentpipe-frontend` cloned next to this repo. More in
[docs/DEPLOYMENT.md](docs/DEPLOYMENT.md), which also covers production on AWS.

Emailed links (verification/reset/invite) are printed to the log in the default
console email mode — see [docs/API.md](docs/API.md#email-linked-flows-verification-reset-invitations).

### Tests

```bash
cd backend
./mvnw test      # unit tests always; integration tests when Docker is running
```

## Configuration

All settings come from environment variables (see `infra/.env.example`):

| Variable | Default | Purpose |
|---|---|---|
| `DB_URL` / `DB_USERNAME` / `DB_PASSWORD` | local `talentpipe` DB | datasource |
| `SERVER_PORT` | `8080` | API port |
| `JWT_SECRET` | — (**required**) | HS256 signing secret, ≥ 32 bytes |
| `JWT_ACCESS_TTL` / `JWT_REFRESH_TTL` | `15m` / `7d` | token lifetimes |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:5173` | SPA origin |
| `RESEND_API_KEY` | empty ⇒ console email mode | email transport |
| `MAIL_FROM` | `onboarding@resend.dev` | sender address |
| `FRONTEND_BASE_URL` | `http://localhost:5173` | base for emailed links |
| `FORWARD_HEADERS_STRATEGY` / `CLIENT_IP_HEADER` | `none` / `X-Forwarded-For` | how to get the client IP behind a proxy (production uses `native` / `X-Viewer-Ip`) |

## Documentation

- [docs/API.md](docs/API.md) — endpoint reference, error model, security behavior
- [docs/DECISIONS.md](docs/DECISIONS.md) — architecture decision records
- [docs/DEPLOYMENT.md](docs/DEPLOYMENT.md) — running locally, AWS setup and operations
- [docs/QUALITY.md](docs/QUALITY.md) — SonarQube/SonarCloud setup
- [CONTRIBUTING.md](CONTRIBUTING.md) — branching model, PR rules, commit style
