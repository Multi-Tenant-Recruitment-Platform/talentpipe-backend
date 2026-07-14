# TalentPipe

**Multi-Tenant Recruitment Intelligence Platform** — one place for a company's jobs, candidates and hiring pipeline, with AI-assisted matching arriving in later sprints.

## Architecture at a glance

TalentPipe is a **modular monolith**:

| Piece | Tech |
|---|---|
| Backend | Java 17, Spring Boot 3.x (Maven), Spring Data JPA + Hibernate, Spring Security, JJWT, Flyway |
| Database | PostgreSQL 15 with **pgvector** (enabled now, used by the AI module later) |
| Frontend | React 18 + TypeScript + Vite, React Router, Tailwind CSS, Axios |
| Local infra | Docker Compose (PostgreSQL only this sprint) |

Backend modules live under `com.talentpipe.<module>` — `auth`, `tenant`, `job`, `candidate`, `pipeline`, `ai`, `notification`, `analytics` — plus the shared `common` kernel and `security` infrastructure. Ground rules: **no cross-module entity imports** (modules talk via services/DTOs), **entities never cross the controller boundary**, **stateless token auth** (no server sessions), and **tenant identity always comes from context** (JWT claim / resolved subdomain), never from a request body. See [docs/DECISIONS.md](docs/DECISIONS.md).

```
/backend    Spring Boot app (modular monolith)
/frontend   React + Vite SPA
/infra      docker-compose.yml + .env.example
/docs       DECISIONS.md (architecture decision records)
/qa         QA workspace: test plan, test cases, API test kit, and the
            evidence trail of executed test runs (qa/test-runs/)
```

## Prerequisites

- **JDK 17** (e.g. [Temurin 17](https://adoptium.net/)) — Maven itself is NOT needed; the repo ships the Maven Wrapper (`mvnw`)
- **Node.js 18+** and npm
- **Docker Desktop** (for PostgreSQL, and for running the integration tests)

## Running everything locally

### 1. Start PostgreSQL

```bash
cd infra
cp .env.example .env        # defaults work for local dev
docker compose up -d
```

This starts `pgvector/pgvector:pg15` (PostgreSQL 15 + pgvector) on port 5432 with database/user/password `talentpipe`.

### 2. Run the backend

The backend fails fast without a JWT secret (≥ 32 bytes) — set it explicitly:

```bash
# PowerShell
$env:JWT_SECRET = "dev-only-secret-at-least-32-bytes-long-change-me"
cd backend
.\mvnw.cmd spring-boot:run
```

```bash
# bash / zsh
export JWT_SECRET="dev-only-secret-at-least-32-bytes-long-change-me"
cd backend
./mvnw spring-boot:run
```

On first boot Flyway migrates the empty database (extensions → auth tables → role seed). The API listens on `http://localhost:8080`.

Environment variables (all optional except `JWT_SECRET` — see `infra/.env.example`):

| Variable | Default | Purpose |
|---|---|---|
| `DB_URL` | `jdbc:postgresql://localhost:5432/talentpipe` | JDBC URL |
| `DB_USERNAME` / `DB_PASSWORD` | `talentpipe` / `talentpipe` | DB credentials |
| `SERVER_PORT` | `8080` | API port |
| `JWT_SECRET` | — (required) | HS256 signing secret, ≥ 32 bytes |
| `JWT_ACCESS_TTL` / `JWT_REFRESH_TTL` | `15m` / `7d` | Token lifetimes |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:5173` | SPA dev origin |

### 3. Run the frontend

```bash
cd frontend
npm install
npm run dev
```

Open `http://localhost:5173`. The dev server proxies `/api` to the backend, so no extra config is needed.

### 4. Register and log in end to end

1. Go to **Get started** (`/register`): enter a company name, a subdomain (e.g. `acme`), and the admin's name/email/password → you land on the login page.
2. Log in with the **subdomain + email + password** (the subdomain is sent as the `X-Tenant-Subdomain` header — see ADR-1 in `docs/DECISIONS.md`).
3. You arrive on the dashboard: *"Welcome, {firstName} — {companyName}"*.

## API — Week 1 endpoints

Base path: `/api/v1`

| Method | Path | Auth | Description |
|---|---|---|---|
| POST | `/auth/register` | public | Company onboarding: creates tenant + first `COMPANY_ADMIN`. `409` on duplicate subdomain. |
| POST | `/auth/login` | public | Header `X-Tenant-Subdomain` + body `{ email, password }` → `{ accessToken, refreshToken, expiresIn, user }`. `401` on any failure. |
| POST | `/auth/refresh` | public | Rotates a refresh token → new token pair. Replay of a consumed token → `401`. |
| POST | `/auth/logout` | bearer | Revokes the presented refresh token (idempotent). |
| GET | `/auth/me` | bearer | Current user's profile. |
| GET | `/public/jobs` | public | Public job board — empty page until the Job module lands. |

Every failure returns the uniform envelope `{ timestamp, status, error, message, path }` with the platform status semantics (400 validation, 401 bad/expired token, 403 wrong role, 404 missing **or cross-tenant**, 409 conflict, 422 business rule, 500 unhandled + correlation id).

## Tests

```bash
cd backend
./mvnw test          # Windows: .\mvnw.cmd test
```

- **Unit tests** always run: JWT issue/validate/expiry/tampering, bcrypt cost 12, registration service.
- **Integration tests** (register→login→me flow, error paths, and the **TenantContext leak guard** — the most important test in the suite) run against real PostgreSQL via Testcontainers and are **skipped automatically when Docker isn't available** (`@Testcontainers(disabledWithoutDocker = true)`). Run them on any machine with Docker running.

Frontend type-check + build: `cd frontend && npm run build`.

## Sprint status

Week 1 scope: platform setup + core authentication. Deferred work is tagged `// TODO(sprint1-w2):` in code — email verification, account lockout (columns already exist), invitations, password reset, candidate accounts, real job data. See `docs/DECISIONS.md`.
