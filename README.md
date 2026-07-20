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
| `RESEND_API_KEY` | — (empty) | Resend key. **Empty ⇒ console email mode** — see [Email](#email-verification-reset-and-invitations) |
| `MAIL_FROM` | `onboarding@resend.dev` | Sender address (needs a verified domain for real recipients) |
| `FRONTEND_BASE_URL` | `http://localhost:5173` | Base URL used to build emailed links |

### Email: verification, reset and invitations

Email drives three flows — account verification, password reset and team invitations —
and the backend has **two modes**, chosen by whether `RESEND_API_KEY` is set:

| Mode | When | Behavior |
|---|---|---|
| **Console** (default) | `RESEND_API_KEY` empty | Nothing is sent. Every link is written to the backend log, prefixed `[EMAIL-CONSOLE]`. Copy it into the browser to complete the flow. **This is the recommended way to run locally** — no account, no secrets. |
| **Resend** | `RESEND_API_KEY` set | Mail is delivered through the Resend API, asynchronously. |

⚠️ **Resend only delivers to arbitrary recipients from a verified sending domain.** With the
default `onboarding@resend.dev` sandbox sender, Resend accepts the request and returns success,
but **only actually delivers to the email address that owns the Resend account** — invitations to
teammates will silently go nowhere. To email real users: verify your domain at
[resend.com/domains](https://resend.com/domains), then set `MAIL_FROM` to an address on it
(e.g. `no-reply@yourcompany.com`).

Never put the key in a file that is committed — export it, or put it in `infra/.env`
(which is gitignored):

```bash
# PowerShell
$env:RESEND_API_KEY = "re_..."
$env:MAIL_FROM = "no-reply@yourcompany.com"
```

Email never blocks or breaks a request: sending happens on a background thread *after* the
database transaction commits, so a mail outage still leaves registration, reset and invitation
succeeding. Failed attempts to company users are recorded in the `notifications` table as
`FAILED` with a correlation id, and users can request a new link from the sign-in page.

### 3. Run the frontend

```bash
cd frontend
npm install
npm run dev
```

Open `http://localhost:5173`. The dev server proxies `/api` to the backend, so no extra config is needed.

### 4. Walk the whole thing end to end

Running in console email mode, so every link comes from the backend log:

1. **Register a company** (`/register`): company name, subdomain (e.g. `acme`), admin details.
2. **Verify the admin.** Find `[EMAIL-CONSOLE]` in the backend log, copy the `/verify-email?token=…`
   link into the browser. (Logging in first shows a `403` telling you to verify — that is the gate working.)
3. **Log in** with **subdomain + email + password** (the subdomain travels as the `X-Tenant-Subdomain`
   header — ADR-1) → the dashboard.
4. **Invite an HR manager** from *Team & invitations* → copy the `/accept-invite?token=…` link from
   the log → set a password → log in with the same subdomain.
5. **Register a candidate** (`/register-candidate`) → verify from the log → log in on the
   **Candidate** tab with **no subdomain**.
6. **Reset a password**: *Forgot password?* → copy `/reset-password?token=…` from the log → set a new
   one. Every existing session for that account is revoked.

## API

Base path: `/api/v1`

| Method | Path | Auth | Description |
|---|---|---|---|
| POST | `/auth/register` | public | Company onboarding: tenant + first `COMPANY_ADMIN`, created `PENDING_VERIFICATION`. `409` on duplicate subdomain. |
| POST | `/auth/login` | public | Optional header `X-Tenant-Subdomain` + body `{ email, password }`. No header ⇒ candidate / super-admin login. `401` bad credentials, `403` unverified or locked, `429` rate-limited. |
| POST | `/auth/verify-email` | public | Consumes a verification token (user *or* candidate) and activates the account. |
| POST | `/auth/resend-verification` | public | New verification link. Always `200` — never reveals whether the address exists. |
| POST | `/auth/refresh` | public | Rotates a refresh token → new pair. Replay of a consumed token → `401`. |
| POST | `/auth/logout` | bearer | Revokes the presented refresh token (idempotent). |
| GET | `/auth/me` | bearer | Current principal's profile (user or candidate). |
| POST | `/auth/forgot-password` | public | Starts a reset. Always `200`. 30-minute single-use link per matching account. |
| POST | `/auth/password-reset/confirm` | public | Sets a new password and revokes every active session. |
| POST | `/auth/accept-invite` | public | Sets the first password and activates an invited account. |
| GET | `/team` | `COMPANY_ADMIN` | Members and pending invitations in the caller's tenant. |
| POST | `/team/invitations` | `COMPANY_ADMIN` | Invites an `HR_MANAGER` or `INTERVIEWER`. `409` if the email already exists here. |
| POST | `/team/invitations/{userId}/resend` | `COMPANY_ADMIN` | Re-sends an invitation. `404` if it isn't in the caller's tenant. |
| DELETE | `/team/invitations/{userId}` | `COMPANY_ADMIN` | Revokes a pending invitation. |
| POST | `/public/candidates/register` | public | Candidate self-registration (no tenant, globally unique email). |
| GET | `/public/jobs` | public | Public job board — empty page until the Job module lands. |

Every failure returns the uniform envelope `{ timestamp, status, error, message, path }` with the platform status semantics (400 validation, 401 bad/expired token or bad credentials, 403 wrong role / unverified / locked, 404 missing **or cross-tenant**, 409 conflict, 422 business rule, 429 rate-limited, 500 unhandled + correlation id).

### Security behavior worth knowing

- **Account lockout**: 5 consecutive failed logins lock an account for 15 minutes (`403` with retry timing). Applies to both company users and candidates.
- **Login rate limit**: 10 requests/minute per IP on `/auth/login`, `/auth/forgot-password` and `/auth/resend-verification` → `429` plus a `Retry-After` header. The limit is per backend instance.
- **Tenant identity** is only ever taken from the JWT claim or the `X-Tenant-Subdomain` header — never from a request body or path parameter.

## Tests

```bash
cd backend
./mvnw test          # Windows: .\mvnw.cmd test
```

- **Unit tests** always run: JWT issue/validate/expiry/tampering, bcrypt cost 12, registration service.
- **Integration tests** (register→login→me flow, error paths, and the **TenantContext leak guard** — the most important test in the suite) run against real PostgreSQL via Testcontainers and are **skipped automatically when Docker isn't available** (`@Testcontainers(disabledWithoutDocker = true)`). Run them on any machine with Docker running.

Frontend type-check + build: `cd frontend && npm run build`.

## Sprint status

**Sprint 1 complete.** Week 1 delivered platform setup + core authentication; Week 2 completed
Epic 1: email verification gating login, password reset, account lockout, candidate registration
and candidate auth, team invitations, RBAC enforcement, and login rate limiting — plus the
event-driven email path behind all of it.

Remaining placeholders are tagged `// TODO(sprint2):` (job module, pipeline data, tenant profile
updates, dashboard metrics still on mock data). Architecture decisions are recorded in
[docs/DECISIONS.md](docs/DECISIONS.md); the Week 2 code audit is in
[docs/SPRINT1_W2_AUDIT.md](docs/SPRINT1_W2_AUDIT.md).
