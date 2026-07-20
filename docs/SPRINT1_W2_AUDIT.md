# Sprint 1 / Week 2 — Repository Audit

**Date:** 2026-07-20 · **Auditor:** Claude (Week 2 kickoff, Phase 0)
**Scope:** full working tree — backend, frontend, Flyway migrations, infra, docs — held against the
source-of-truth architecture in the Week 2 brief. No fixes applied yet; this document is the input to Phase 1.

Severity scale: **BLOCKER** (must fix before anything ships) · **MAJOR** (broken behavior or architecture
violation) · **MINOR** (hygiene, polish, docs).

---

## 1. Inventory — what exists today

### Backend (`com.talentpipe.*`)

| Module | Contents | Epic 1 coverage |
|---|---|---|
| `common` | BaseEntity, TenantContext (ThreadLocal), PageResponse, uniform error envelope + GlobalExceptionHandler | cross-cutting |
| `security` | JwtTokenProvider (HS256, typed ACCESS/REFRESH, env-only secret, fail-fast <32 bytes), TenantResolvingFilter (**leak guard intact** — `finally { TenantContext.clear() }`), JwtAuthenticationFilter, SecurityConfig (bcrypt cost 12, stateless, CORS) | PB-007 foundation |
| `tenant` | Tenant entity/repo/service/DTOs, subdomain uniqueness | PB-001 |
| `auth` | User/Role/RefreshToken entities; AuthService (register, login incl. candidate+super-admin branches, refresh, logout, me); RefreshTokenService (hashed + rotated); EmailVerificationService; PasswordResetService; EmailVerificationToken + PasswordResetToken entities; AuthController | PB-001, PB-007, PB-008 (partial — see bugs) |
| `candidate` | Candidate + CandidateVerificationToken entities/repos, CandidateService (register), PublicCandidateController | PB-006 (partial) |
| `notification` | EmailService interface, ConsoleEmailService, ResendEmailService (`@Primary`) | infrastructure for PB-001/006/008 (badly — §3) |
| `job` | Public jobs stub (empty page) | out of Epic 1 |
| `pipeline`, `ai`, `analytics` | placeholders | — |

### Endpoints

| Endpoint | Status |
|---|---|
| POST `/auth/register` | works, but **skips verification** (creates ACTIVE) |
| POST `/auth/login` | company (header), candidate (no header), super-admin (no header — **dead code**, see B-6) |
| POST `/auth/refresh`, `/auth/logout`, GET `/auth/me` | works for users; **refresh broken for candidates** (B-4) |
| POST `/auth/verify-email` | works (users + candidates) but pointless while registration creates ACTIVE accounts |
| POST `/auth/password-reset/request` | works but takes **subdomain in the body** (B-5) |
| POST `/auth/forgot-password` | works but picks an **arbitrary account** on multi-tenant emails (B-7) |
| POST `/auth/password-reset/confirm` | works (users + candidates) |
| POST `/public/candidates/register` | works, but creates ACTIVE (B-2) |
| GET `/public/jobs` | stub, fine |

### Flyway migrations

V1 extensions · V2 auth tables · V3 role seed (Week 1, untouched — good) · V4 verification + reset tokens
(+ `PENDING_VERIFICATION` status) · V5 candidates + candidate verification tokens ·
V6 **drops the FKs** on refresh/reset/verification token tables so candidate ids can live in them.
All forward-only, no edits to applied files. V6 is a questionable design (§ B-4 / D-2) but is applied history — do not edit.

### Frontend (React SPA)

Pages: Landing, Jobs, Register (company), CandidateRegister, Login (candidate/company tabs, sends
`X-Tenant-Subdomain` only for company — correct), ForgotPassword, dashboard (Overview, Team, Pipeline,
Settings) behind ProtectedRoute. Token storage: access in memory, refresh in localStorage; 401→refresh
interceptor is single-flight with a `_retried` guard (no infinite loop) — intact.
**Dashboard Team/Overview/Pipeline pages run entirely on `data/mockDashboard.ts`** — the "invitation
feature" is UI-only, no API behind it.

### Epic 1 story gap list

PB numbering per the Week 2 brief; where the brief doesn't name a number, the feature is listed by name.

| Story | Status | Notes |
|---|---|---|
| PB-001 company registration + email verification gating login | **Partial** | register works; verification bypassed (B-1), no resend endpoint, unverified login is generic 401 not actionable 403 |
| PB-002 RBAC enforcement | **Missing** | zero `@PreAuthorize`/role checks anywhere; `@EnableMethodSecurity` is on but unused |
| PB-003 invite HR managers | **Missing** (backend) | frontend modal is a mock |
| PB-004 invite interviewers | **Missing** (backend) | same |
| PB-006 candidate registration + candidate auth | **Partial** | register + login exist; created ACTIVE (B-2); candidate **refresh broken** (B-4) |
| PB-007 login (both personas) | **Partial** | works, but no lockout, no rate limit, super-admin branch dead (B-6) |
| PB-008 password reset | **Partial** | backend flows exist (2 overlapping endpoints, B-5/B-7); **no frontend page for the reset link** (B-3) |
| Account lockout (5 fails / 15 min) | **Missing** | columns exist since V2, logic never written (TODO markers still in AuthService) |
| Login rate limiting (10/min/IP → 429) | **Missing** | no 429 mapping in the error handler either |

---

## 2. Bug & mis-implementation list

### Blockers

**B-1 — Email verification disabled by "testing" bypass** — `AuthService.register`
(`auth/service/AuthService.java:88-96`) creates the company admin with `UserStatus.ACTIVE` and the comment
*"bypass verification blocker during testing"*. A verification email is still sent, but it verifies nothing.
PB-001's core acceptance criterion (unverified accounts cannot log in) is dead.
**Fix:** create as `PENDING_VERIFICATION`; login returns 403 with an actionable message for unverified
accounts (per brief); add `/auth/resend-verification`.

**B-2 — Same bypass for candidates** — `candidate/service/CandidateService.java:61` passes
`UserStatus.ACTIVE` explicitly (the entity default and the DDL default are both `PENDING_VERIFICATION`;
the constructor overrides them). **Fix:** create as `PENDING_VERIFICATION`.

**B-3 — Emailed links point at SPA routes that don't exist** — backend builds
`{base}/verify-email?token=…` and `{base}/reset-password?token=…`, but `frontend/src/App.tsx` has **no**
`/verify-email` or `/reset-password` route. Every verification and reset email dead-ends on a blank route;
PB-008 cannot complete end-to-end from the browser. **Fix:** add both pages + routes.

**B-4 — Candidate refresh is broken (15-minute sessions)** — `AuthService.refresh`
(`AuthService.java:182-184`) resolves the refresh-token owner via `userRepository.findById(userId)` only.
Candidate ids are not in `users` (V6 dropped the FK precisely so candidate ids could be stored in
`refresh_tokens`), so a candidate's refresh always throws `InvalidTokenException` → the SPA silently logs
the candidate out when the access token expires. **Fix:** fall back to `candidateRepository` (mirroring
`getCurrentUser`) and issue a CANDIDATE token pair.

**B-5 — Committed live Resend API key** — `application.yml:57` and `ResendEmailService.java:33` both carry
`re_P7LBeJ1E_9WPE3QWVsHQD3hGDaPU5YVkS` as an in-repo default. This is a real secret in version control;
it must be treated as **compromised and rotated in the Resend dashboard** (history rewrite is the repo
owner's call — the key is in at least one pushed/committed revision). **Fix in code:** no default at all —
key comes only from `RESEND_API_KEY`; missing key = console/log mode, never a hidden shared key.

### Major

**B-6 — Super-admin global login is dead code** — `AuthService.login:134` calls
`userRepository.findByTenantIdAndEmail(null, email)`. Spring Data binds the null parameter as
`tenant_id = NULL`, which matches no rows in SQL — a SUPER_ADMIN can never log in through this branch.
**Fix:** dedicated `findByTenantIdIsNullAndEmail(email)` query method.

**B-7 — Global forgot-password resets an arbitrary account** — `PasswordResetService.forgotPassword:144`
takes `findAllByEmail(email).get(0)` with the comment "for testing". The same email can exist in several
tenants; which account gets reset is accidental (insertion order). **Fix:** issue a reset token per matching
account (each email identifies its own account via its token) — deterministic and still enumeration-safe.

**B-8 — Tenant identity in a request body** — `PasswordResetRequestDto` carries `subdomain` in the JSON
body for `/auth/password-reset/request`, directly violating "tenant identity … NEVER from a request body or
path param". The frontend only uses `/auth/forgot-password` (global), so the offending endpoint has no
caller. **Fix:** remove the endpoint + DTO; standardize on the global forgot flow fixed per B-7 (optionally
accepting `X-Tenant-Subdomain` as a header to narrow scope). Trade-off noted in §4.

**B-9 — Secret links logged at INFO** — `ResendEmailService.sendVerificationEmail/sendPasswordResetEmail`
log the full link — i.e. the raw single-use token — as `[EMAIL-FALLBACK]` on **every** send, key configured
or not. Anyone with log access can take over any account mid-reset. Console-logging links is a legitimate
*dev-mode* behavior (ConsoleEmailService) but must never be the unconditional production path.

**B-10 — Cross-module entity/repository imports** — the architecture forbids modules touching each other's
entities/repos, yet: `auth.AuthService`, `auth.EmailVerificationService`, `auth.PasswordResetService`
import `candidate.repository.*` and `candidate.entity.Candidate` directly (always via fully-qualified
names — the code smells like it knows it's trespassing); `candidate.CandidateService` imports
`auth.entity.RoleName`/`UserStatus` and returns `auth.dto.UserResponse`. **Proposed fix (surgical, not a
rewrite):** candidate module exposes its operations through `CandidateService` methods returning
candidate-owned DTOs; auth depends on that service API only. Shared status vocabulary either stays put
(enums are borderline) or moves to `common`. Flagging per the brief before restructuring anything.

**B-11 — Email sending: raw threads, no timeouts, no persistence** — `ResendEmailService.sendEmail` spawns
a **new `Thread` per email** (unbounded, no pool, dies with errors unrecorded) and the shared `RestTemplate`
has default (infinite) connect/read timeouts, so a hung Resend call can pin threads forever. No
notification attempt is persisted anywhere (no `notifications` table exists in any migration), there is no
FAILED marking and no resend path. This is the Phase 2 rebuild target (§3).

### Minor

**M-1** — `Candidate.toString()` includes the email (PII in logs); `CandidateService.register` also logs
the candidate email at INFO. `User.toString()` deliberately excludes it — match that.
**M-2** — `frontend/` was renamed **`Frontend/`** (capital F) and `infra/docker-compose.yml` references
`../Frontend`. Works on Windows/macOS; on a Linux checkout the folder name is whatever git tracked, and a
case-only rename made on a case-insensitive filesystem can leave the index tracking either or both names.
README/docs still say `/frontend`. Owner should verify with `git ls-files | grep -i frontend` and pick ONE
casing (recommend lowercase `frontend`, the original).
**M-3** — The `qa/` workspace (test plan, 51 cases, evidence trail) is **gone from the working tree**. If
that was intentional (moved to another repo?) fine — otherwise the QA teammate's audit trail was deleted.
Flagging for the owner; not restoring it myself.
**M-4** — `infra/.env.example` and the compose `backend` service are missing `RESEND_API_KEY`,
`MAIL_FROM`/`RESEND_FROM_EMAIL`, and the frontend-base-URL variable — the email feature is unconfigurable
without reading source.
**M-5** — Env naming drift vs the brief: code uses `APP_BASE_URL` / `RESEND_FROM_EMAIL`; the brief
specifies `FRONTEND_BASE_URL` / `MAIL_FROM`. Phase 2 will standardize on the brief's names (keeping the
old ones as fallback aliases is unnecessary — nothing deployed yet).
**M-6** — Duplicate `mapCandidateToUserResponse` in `AuthService` and `CandidateService` (same 20 lines).
Consolidate when B-10 is addressed.
**M-7** — Two overlapping reset-request endpoints (`/password-reset/request`, `/forgot-password`) and the
brief names them `/auth/password/forgot` + `/auth/password/reset`. Consolidation decision in §4.
**M-8** — Post-registration UX says "log in to continue"; once B-1/B-2 land it must say "check your email
to verify your account" (both register pages + login notice).
**M-9** — No 429 handling anywhere (needed for rate limiting, Phase 3.7).
**M-10** — `ConsoleEmailService` javadoc claims it is `@Primary` but the annotation is on
`ResendEmailService` (import left unused). Cosmetic, but the doc lies.

### Explicitly verified healthy

- JWT: secret env-only with fail-fast (no default in `application.yml`), expiry + tampering rejected via
  full JJWT parse, claims correct (`sub`, nullable `tenant_id`, `role`, `email`), type confusion blocked.
- **TenantContext leak guard present** (`TenantResolvingFilter` `finally` clear) — the critical safety
  property holds; guard test still in the test tree.
- bcrypt cost 12; refresh tokens hashed (SHA-256) + rotated + revoked on logout; reset revokes all sessions.
- Uniform error envelope on all handler paths incl. the security filter chain; 404-over-403 convention.
- DTO boundary: no entity or `password_hash` crosses any controller; `UserResponse` has no password field.
- CORS: SPA origin allowed, `X-Tenant-Subdomain` in allowed headers.
- Frontend interceptor loop guard, protected routes, header-based tenant at login.

---

## 3. Resend email integration review (Phase 2 target)

Current implementation (`notification/ResendEmailService.java`) verdict: **wrong on every axis the brief
lists.**

1. **Secret management:** live API key committed as a code+config default (B-5). Rotate the key.
2. **Blocking/async model:** raw `new Thread()` per send — no executor, no backpressure, no domain events.
   Brief requires the event-driven path: action publishes a domain event → `@Async` listener sends.
   There is no `@EnableAsync`, no events, no listener today.
3. **Failure handling:** failures are only logged (in a throwaway thread); nothing is marked FAILED, no
   resend endpoint exists. (Registration does *survive* email failure today, but only accidentally —
   because the thread swallows everything, success is also unverifiable.)
4. **Token/link hygiene:** raw tokens logged at INFO on every send (B-9). Links themselves are correctly
   built from config (no hardcoded localhost) and tokens are stored hashed, single-use, expiry-enforced —
   the *token* layer (teammates' `EmailVerificationService`/`PasswordResetService`) is solid.
5. **Persistence:** no `notifications` table exists yet. Phase 2 adds it per the design doc (tenant-scoped,
   references `users(id)`, PENDING → SENT/FAILED). **Candidate emails do not fit that table** (no tenant,
   candidate id not in `users`) — they will be sent through the same async path but not recorded in the
   tenant-scoped table (documented gap; a `candidate_notifications` table is a later decision, not worth
   jamming now).
6. **Deliverability caveat:** with the `onboarding@resend.dev` test sender, Resend delivers **only to the
   account owner's own address**. Emailing arbitrary recipients requires a verified sending domain. Must be
   documented in README; dev mode = console logging, explicitly configured.
7. HTML built by `String.format` with unescaped interpolation — fine while links are the only input, worth
   an escape once user-supplied text (invite notes) enters templates.

---

## 4. Decisions proposed (veto window)

| # | Decision | Trade-off |
|---|---|---|
| D-1 | **Delete** `/auth/password-reset/request` (+DTO) rather than fix it; keep one global forgot endpoint. Keep existing paths `/auth/forgot-password` + `/auth/password-reset/confirm` rather than renaming to the brief's `/auth/password/forgot|reset` — the frontend already calls the former and the brief's names read as intent, not contract. | Removes a teammate's endpoint — but it has no caller and violates the tenant-in-body rule. |
| D-2 | Keep V6's shared token tables (candidate ids in `refresh_tokens` / `password_reset_tokens`) — applied migration history; re-normalizing now costs a week we don't have. Add the missing owner-resolution logic instead (B-4). | Weaker referential integrity (no FK); revisit when candidates get real volume. |
| D-3 | Fix B-10 by routing candidate access through `CandidateService`-level APIs, moving nothing wholesale. Enum sharing (`UserStatus`, `RoleName`) stays as-is this sprint. | Leaves the enum coupling; full extraction is a refactor the brief tells me to ask about first. |
| D-4 | Standardize env names on the brief: `FRONTEND_BASE_URL`, `MAIL_FROM`, `RESEND_API_KEY`. | Anyone with a local `.env` using `APP_BASE_URL` must rename once. |
| D-5 | New Flyway `user_tokens`-style migration is **not** needed — V4/V5 already provide equivalent per-type token tables (brief: "UNLESS a teammate already added an equivalent table, in which case reuse it"). Invites get their own `invitations` table (needs `tenant_id`, `role_id`, invited-by — shape differs from the existing token tables). | Three token tables instead of one generic — consistent with what's applied. |

---

## 5. Resolution log (updated after the fix phases)

Every blocker and major is fixed; the audit above is kept as the original findings record.

| ID | Status | Where it was fixed |
|---|---|---|
| B-1 verification bypass (users) | **Fixed** | `AuthService.register` creates `PENDING_VERIFICATION`; `authenticateUser` gates login with a 403 |
| B-2 verification bypass (candidates) | **Fixed** | `CandidateService.register` creates `PENDING_VERIFICATION`; gate in `CandidateAuthService.authenticate` |
| B-3 dead-end email links | **Fixed** | New `VerifyEmailPage`, `ResetPasswordPage`, `AcceptInvitePage` + routes in `App.tsx` |
| B-4 candidate refresh broken | **Fixed** | `AuthService.refresh` resolves users then candidates |
| B-5 committed API key | **Fixed in code** — key must still be **rotated in Resend** | No default in `application.yml` or `EmailConfig` |
| B-6 dead super-admin login | **Fixed** | `UserRepository.findByTenantIdIsNullAndEmail` |
| B-7 arbitrary account reset | **Fixed** | `PasswordResetService.forgotPassword` issues one token per matching account |
| B-8 tenant in request body | **Fixed** | `/auth/password-reset/request` + DTO removed (D-1) |
| B-9 tokens logged at INFO | **Fixed** | Links logged only in console mode; Resend path logs recipient + subject |
| B-10 cross-module imports | **Fixed** | `CandidateAuthService` / `CandidateVerificationService` are the seam; no repo/entity crossing |
| B-11 raw threads, no persistence | **Fixed** | Event → `@TransactionalEventListener` + `@Async` bounded pool; `notifications` table (V7) |
| M-1 PII in logs | **Fixed** | `Candidate.toString()` and registration logs use ids only |
| M-4/M-5 env config | **Fixed** | `RESEND_API_KEY`, `MAIL_FROM`, `FRONTEND_BASE_URL` in `.env.example`, compose and README |
| M-6 duplicated mapping | **Fixed** | Single `CandidateAuthService.toProfile` |
| M-8 post-registration UX | **Fixed** | Login page now says to check email for the verification link |
| M-9 no 429 | **Fixed** | `LoginRateLimitFilter` + `RateLimiter` |
| M-10 misleading `@Primary` javadoc | **Fixed** | Transport selection moved to `EmailConfig` |
| M-2 `Frontend/` casing | **Open — owner action** | Needs `git ls-files` check; not touched (git is the owner's) |
| M-3 missing `qa/` workspace | **Open — owner action** | Not restored; flagged for the owner |

Also delivered in Phase 3: account lockout (5 → 15 min, both identities, `LockoutPolicy`),
invitations end-to-end (`invitation_tokens`, `InvitationService`, `TeamController`, real
`TeamPage`), RBAC via `@PreAuthorize` + service-layer scope re-checks, and login rate limiting.

**Not done (deliberate):** the stretch items — general 100/min authenticated rate limit and the
Hibernate ORM-level `tenant_id` `@Filter`. Priorities 1–7 were finished properly first, per the
brief's instruction.

## 6. Runtime verification status

**Verified on this machine:** `./mvnw test` → 50 tests, 0 failures (21 Testcontainers tests skipped,
no Docker); `npm run build` → type-check and production build clean.

**NOT verified — needs a Docker-equipped machine:** application boot, Flyway V1→V7 on an empty
database, and every end-to-end flow. The migrations and runtime wiring are unexecuted code until
someone runs them. This is the single biggest gap in this hand-off: V7 in particular
(`notifications`, `invitation_tokens`, the `candidates` lockout columns) has never been applied.
