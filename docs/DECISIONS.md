# TalentPipe — Architecture Decision Records

Short, dated records of decisions that shape the codebase. Newest last.

---

## ADR-1 — Tenant resolution at login via `X-Tenant-Subdomain` header (Week 1)

**Date:** 2026-07-14 · **Status:** Accepted (transitional)

### Context

Users are unique per `(tenant_id, email)`, not globally — the same email can
hold accounts in two different companies. Login therefore MUST know which
tenant it is authenticating against before it can even look the user up.

In production the tenant is identified by the subdomain the browser is on
(`acme.talentpipe.io` → tenant `acme`), derived server-side from the **Host
header**. Local development has no real subdomains on `localhost`, and
Week 1 has no ingress/DNS setup.

### Decision

For Week 1, `POST /api/v1/auth/login` resolves the tenant from an explicit
**`X-Tenant-Subdomain` request header**. The login body stays exactly
`{ email, password }`; the SPA's login form collects the subdomain and sends
it as that header.

Two principles preserved by this choice:

1. **Tenant identity never travels in a request body or path parameter** — a
   header is transport context, swapped later for the Host header without any
   change to the body contract.
2. The login failure for a wrong subdomain, unknown email or wrong password is
   the same generic `401` — account existence never leaks across tenants.

### Production follow-up

Replace the header source with Host-header parsing at the edge (keeping the
header as a dev/testing override), once real subdomains exist. The seam is a
single line in `AuthController`.

---

## ADR-2 — Account-lockout columns now, lockout logic in Week 2 *(resolved)*

**Date:** 2026-07-14 · **Status:** Accepted

### Context

Brute-force protection (failed-attempt counting + temporary lockout) is
scheduled for Week 2, but the `users` table ships in Week 1's
`V2__auth_tables.sql`.

### Decision

`users.failed_login_count` (SMALLINT NOT NULL DEFAULT 0) and
`users.locked_until` (TIMESTAMPTZ NULL) are created **now**, unused. This
avoids an `ALTER TABLE` migration one week after the table's birth and keeps
the Week 2 change purely behavioral (service-layer logic + tests).

**Resolved in Week 2:** the logic now lives in `AuthService.authenticateUser`
and `CandidateAuthService.authenticate`, with the policy constants shared in
`common/util/LockoutPolicy` (5 attempts → 15 minutes). `V7` added the same two
columns to `candidates`, since candidates authenticate through a separate path
and needed identical protection.

The lock is checked **before** the password comparison: a locked account stays
locked even for the correct password, otherwise the lock means nothing.

---

## ADR-3 — Candidates are a separate identity, not users with a role

**Date:** 2026-07-20 · **Status:** Accepted

### Context

Both company staff and candidates authenticate, and it is tempting to make
`CANDIDATE` just another row in `users`. The two are structurally different:
company users are unique per `(tenant_id, email)` and always belong to a tenant;
candidates are tenant-independent with a globally unique email, and a single
candidate applies to many companies.

### Decision

Candidates live in their own `candidates` table with a globally unique email,
and their tokens carry `role=CANDIDATE` and **no** `tenant_id` claim. Login
distinguishes the two by the presence of the `X-Tenant-Subdomain` header:
present ⇒ company user, absent ⇒ candidate (then SUPER_ADMIN).

Module boundaries follow the same split. The candidate module owns every
candidate password comparison and exposes only `CandidateProfile` through
`CandidateAuthService`; the auth module never sees a `Candidate` entity or its
hash. Before Week 2 the auth services imported candidate repositories directly —
that coupling is now removed.

### Consequence

An email can legitimately identify a candidate account *and* company accounts in
several tenants at once. Forgot-password therefore issues one token per matching
account rather than guessing which was meant.

---

## ADR-4 — Shared token tables across both identities

**Date:** 2026-07-20 · **Status:** Accepted (with a known trade-off)

### Context

A teammate's `V6` migration dropped the foreign keys on `refresh_tokens`,
`password_reset_tokens` and `email_verification_tokens` so candidate ids could be
stored in them, since candidates are not `users` rows.

### Decision

Keep it. Re-normalizing into per-identity token tables is a schema change to
already-applied migration history, and the sprint has better uses for that time.
The `owner id` in those tables is resolved against `users` first, then
`candidates`.

Candidate email verification is the exception — it has its own
`candidate_verification_tokens` table with a real FK, because it was created that
way and the FK is worth keeping.

### Trade-off

Weaker referential integrity: an orphaned token row is possible if an account is
deleted. Accepted for now because tokens are single-use and short-lived; revisit
when candidate volume justifies it.

---

## ADR-5 — Notifications are tenant-scoped; candidate email is not recorded

**Date:** 2026-07-20 · **Status:** Accepted

### Context

The design doc specifies a `notifications` table that is tenant-scoped and
references `users(id)`. Candidates have neither a tenant nor a `users` row.

### Decision

`notifications` records outbound email for **company users only** (verification,
password reset, invitation) with a `PENDING → SENT | FAILED` lifecycle. Candidate
email is delivered through exactly the same async path but is **not** persisted:
forcing it into the table would mean either a nullable `tenant_id` (destroying
the scoping that makes the table safe to query per tenant) or a fake tenant.

SUPER_ADMIN mail is unrecorded for the same reason — no tenant.

### Consequence

Candidate delivery failures are visible in logs (with a correlation id) but not
queryable per tenant. A `candidate_notifications` table is the obvious follow-up
if candidate email needs an audit trail; deliberately deferred rather than
bolted on.

---

## ADR-6 — Email is event-driven and never fails the request

**Date:** 2026-07-20 · **Status:** Accepted

### Context

The first Resend integration called the mail API from the request thread via a
raw `new Thread()` per email, with no timeouts, no failure record and no pool.

### Decision

Business services never call a transport. They publish a
`NotificationRequestedEvent`; `NotificationDispatcher` consumes it with
`@TransactionalEventListener` (AFTER_COMMIT) + `@Async` on a bounded pool.

Two properties come from that pairing:

1. **After commit** — no email is ever sent for a transaction that rolled back,
   and the token the link points at is guaranteed to exist when it arrives.
2. **Async** — Resend latency never becomes API latency, and a mail outage marks
   the attempt FAILED instead of 500-ing registration, reset or invitation.

The transport is chosen at startup by whether `RESEND_API_KEY` is set: real
sending when it is, console logging when it is not. There is deliberately **no
default API key** — the previous default was a live key committed to the repo.

### Note

Links (which carry live single-use tokens) are logged **only** in console mode,
where nothing is being delivered. The Resend path logs recipient and subject only.

---

## ADR-7 — Tenant isolation enforced in the ORM, not just by discipline

**Date:** 2026-08-09 · **Status:** Accepted

### Context

The isolation story requires that *all* queries are scoped by tenant id and
that cross-tenant access is denied. Until now that held only by discipline:
every service had to remember the `tenant_id` predicate (or an ownership
`.filter(...)`) by hand. One forgotten `findById` in a future module would
silently serve another company's data — the worst failure mode this platform
has.

### Decision

Three independent layers, each sufficient to stop a cross-tenant read:

1. **Context** (existing): `TenantContext` is populated only from the verified
   JWT and cleared in a `finally` block (leak-guard tested).
2. **ORM filter** (new): a Hibernate `@FilterDef`/`@Filter`
   (`tenant_id = :tenantId`, `applyToLoadByKey = true`) on every tenant-scoped
   entity (`User`, `Notification` today). `TenantFilterAspect` enables it on
   the transaction's session for any repository call made while a tenant is in
   context, so the predicate is appended at the SQL level to every query —
   including loads by primary key. A cross-tenant row is not "forbidden", it is
   *invisible*: lookups come back empty and surface as **404**, never
   confirming existence (standing convention).
3. **Service checks** (existing): explicit tenant-scope re-checks (e.g. in
   `InvitationService`) remain as an independent layer.

The filter is deliberately NOT enabled when no tenant is in context:
unauthenticated flows (login, register, emailed-link endpoints), candidates and
SUPER_ADMIN legitimately query across tenants (global login's find-by-email,
global password reset), and the async email threads carry no context at all.

### Consequences

- New tenant-scoped entities must add one `@Filter` line and their table gets
  isolation for free — the default becomes *scoped*, not *global*.
- `@Filter` does not apply to queries that bypass Hibernate (native SQL with
  its own session handling) — none exist today; if one is ever added it must
  scope by hand and say so in review.
- The limit is per JVM instance state only in the aspect; the filter itself is
  per-session, so pooled threads/sessions cannot leak a stale tenant.
- Guarded by `TenantIsolationIntegrationTest`, including a test that bypasses
  the service layer entirely to prove the ORM layer alone blocks cross-tenant
  reads (`findAll` narrowed, foreign `findById` empty, unscoped again once the
  context is cleared).

---

## Standing conventions (not individually numbered)

- **Deferred-work marker:** `// TODO(sprint2): ...` — grep for it at sprint
  planning. (The Week 2 markers are all resolved; anything still tagged for a
  sprint is genuinely outstanding.)
- **Tokens in links** (verification, reset, invite) are 32 random bytes stored
  as SHA-256 hashes only, single-use (`used_at`), with server-side expiry:
  verification 24 h, reset 30 min, invitation 7 days.
- **Role checks happen twice** for sensitive operations: `@PreAuthorize` on the
  controller for the role, plus an independent tenant-scope re-check in the
  service, so a future caller reaching the service another way cannot bypass it.
- **404 over 403 for cross-tenant access:** a resource that exists but belongs
  to another tenant is indistinguishable from one that doesn't exist.
- **Refresh tokens are stored hashed (SHA-256) and rotated on every use**; raw
  tokens exist only in transit and in the client.
- **TenantContext lifecycle:** set only by `TenantResolvingFilter`, cleared in
  its `finally` block; guarded by `TenantContextLeakIntegrationTest`.
