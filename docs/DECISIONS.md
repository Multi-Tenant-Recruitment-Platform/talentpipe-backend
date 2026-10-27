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

## ADR-7 — The vacancy lifecycle is enforced by the entity, not by its callers

**Date:** 2026-10-03 · **Status:** Accepted

### Context

A vacancy moves `DRAFT → PUBLISHED → CLOSED → ARCHIVED` (PB-018 → PB-022). Three
rules must hold however the vacancy is reached: status follows only those
arrows; a published vacancy is always complete; a closed or archived one is
never edited. Checked in each service method, every new method is a new chance
to forget one.

### Decision

- `VacancyStatus` owns the transition table; `JobVacancy` has no setters and
  changes only through `applyContent`, `publish`, `close` and `archive`, each of
  which refuses an illegal move with a `422`. The service orchestrates and
  scopes by tenant; it cannot bypass the rules.
- "Create as published" is a draft published in the same transaction — there is
  one route into `PUBLISHED`, so one place completeness is checked.
- Status changes are their own endpoints, never a field on `PUT`. `PUT` carries
  the `version` it was based on (`@Version`); a stale one is `409`.
- Nothing deletes a vacancy. Archiving is a status, which is what keeps the row
  — and its `publishedAt` / `closedAt` / `archivedAt` — available to reporting.
- CHECK constraints in `V12` restate the invariants (legal statuses, and which
  timestamps a status implies), so a row written outside the application cannot
  hold a state the state machine could never produce.
- The machine is one-way because the backlog defines no reopen or unarchive.
  Adding one is a change to the table in `VacancyStatus` (and the frontend's
  `VACANCY_TRANSITIONS`).

### "Closed vacancies stop accepting applications"

The applications module must not read a vacancy's status. It calls
`VacancyApplicationGate.requireOpen(vacancyId)` inside its submitting
transaction. The gate takes a shared row lock (`SELECT … FOR SHARE`), so a
concurrent close waits for in-flight applications and every later application
sees `CLOSED` — there is no window in which an application lands just after a
close. Applicants do not block each other. A deadline that has passed refuses
applications too, without changing the status: auto-closing at the deadline is
a product decision still open.

### Deadlines are judged in the tenant's timezone

A deadline is a calendar day. "Today" is taken in the timezone on the company
profile (UTC if unset), so a Colombo company's vacancy is publishable, and
open, until midnight in Colombo rather than in UTC.

---

## ADR-8 — Public job search runs on PostgreSQL full-text search

**Date:** 2026-10-03 · **Status:** Accepted

### Context

Candidates search published vacancies by keyword and filter by category and
location (PB-017). The data already lives in PostgreSQL; the volume is thousands
of live adverts, not millions.

### Decision

No separate search engine. A second system would have to be kept in step with
the first, and "edits are reflected immediately" would become "eventually".

- **Keyword**: a `tsvector` column that PostgreSQL **generates** from the advert
  (title A, skills and department B, summary and location C, description D),
  queried with `websearch_to_tsquery` and ranked with `ts_rank`. Being generated,
  it is rewritten in the same transaction as the row — there is nothing to
  reindex and no way for it to drift.
- **Category** is the vacancy's `department`, matched whole and case-insensitively.
  **Location** is a case-insensitive "contains", served by a trigram index.
- **Indexes** are partial on `status = 'PUBLISHED'`: they hold only live adverts,
  so drafts and the growing archive cost searches nothing.
- **The SQL is assembled per request** from fixed fragments, containing only the
  filters in use (`PublicJobSearchQuery`). The one-statement alternative
  (`:x IS NULL OR …`) hides the active filters from the planner. Every
  user-supplied value is a bound parameter.
- **GIN indexes use `fastupdate = off`.** With the default, new entries wait in a
  pending list until vacuum and the planner avoided the index entirely on a
  freshly loaded table. Publishes are rare and searches are constant.
- **Nothing is cached**, so a publish, edit or close is visible on the next request.
- **Public URLs** use `title-company-<id as 32 hex>`; only the id is looked up.
  Unique by construction, stable when a title is edited, nothing to store.
- The job module does not join `tenants`. Company names and logos for a page of
  results come from `TenantService.findCompanySummaries` in one call.

`PublicJobSearchIntegrationTest` runs `EXPLAIN` on the production statements and
fails if any stops using its index.

### Known limits

- The text-search configuration is `english`: stemming and stop words are
  English-only, and a query made only of stop words (including "IT") matches
  nothing — the category filter covers that case.
- `C++`, `C#` and `C` all index as `c`. `Node.js` and `CI/CD` are indexed both
  whole and split, so `node` and `ci` find them.
- Paging is by offset. Fine at this scale; switch to keyset on
  `(published_at, id)` if deep paging ever matters.
- If the function behind the generated column changes, existing rows keep their
  old vector until rewritten (`UPDATE job_vacancies SET title = title`).
- Vacancies of suspended tenants are not yet filtered from the board; nothing
  can suspend a tenant today (`TODO(sprint3)` in `PublicJobService`).

---

## ADR-9 — Company-defined roles built from a permission catalogue

**Date:** 2026-10-23 · **Status:** Accepted

### Context

Companies do not all hire with an "HR manager" and an "interviewer". One wants
a coordinator who runs the team and the interview schedule; another wants a
panel member who only conducts interviews. Fixed roles cannot express that, and
checks such as `hasRole('HR_MANAGER')` tie every endpoint to one company's
org chart.

### Decision

- **Permissions are code, roles are data.** `Permission` is the catalogue — a
  stable key, a group, a label and a description each, plus what it needs
  (`team.invite` needs `team.view`). A company composes roles from it
  (`tenant_roles`, `tenant_role_permissions`, V16). Endpoints check permissions
  (`@RequirePermission`, `@RequireCompanyMember`), never role names.
- **Three default roles** per workspace: Owner (every permission, always; not
  editable), Recruiter and Interviewer (editable, not deletable).
- **Resolved per request.** The token says who you are, not what you may do.
  `RolePermissionResolver` loads the member's current permissions, cached per
  user for at most a minute and evicted when a role or member changes. A change
  takes effect on the next request, with no re-login and no stale token.
- **Guardrails:** nobody grants or revokes a permission they do not hold; only
  an Owner makes or moves an Owner; the last active Owner cannot be moved; a
  role in use is deleted only by reassigning its members; role edits use
  optimistic locking; every change is audited (`tenant_role_audit_events`).
- **The database keeps a role in its tenant**: a member's role is a composite
  foreign key `(tenant_role_id, tenant_id)`.
- **Transition:** `users.role_id` stays as a nullable mirror of the default
  roles, and their members also carry the old `ROLE_COMPANY_ADMIN` /
  `ROLE_HR_MANAGER` / `ROLE_INTERVIEWER` authorities, so branches written
  against fixed roles keep working. A custom role gets neither, so an old
  check fails closed. Responses keep `role` (`CUSTOM` for a custom role) next to
  the new `roleId`, `roleName` and `permissions`. Remove both in sprint 3
  (`TODO(sprint3)` in `Authorities`).
- `EndpointAuthorizationDriftTest` fails the build if an endpoint outside
  `/auth` and `/public` has no authorization annotation.

### Moving a branch from roles to permissions

| Old check | Use instead |
| --- | --- |
| `hasRole('COMPANY_ADMIN')` on team endpoints | `team.view` / `team.invite` / `team.manage` |
| `hasRole('COMPANY_ADMIN')` on company profile, logo, cover | `settings.edit` |
| `hasAnyRole('COMPANY_ADMIN','HR_MANAGER')` reading vacancies | `@RequireCompanyMember`, scoped in the service (ADR-11) |
| `hasAnyRole('COMPANY_ADMIN','HR_MANAGER')` writing vacancies | `jobs.manage`; publish / close / archive `jobs.publish` |
| pipeline templates (`Pipeline-Template-Creation`) | `pipeline.templates.manage` |
| listing applications (`Job-Application-Submission-&-Tracking`) | `applications.view.all`, or scope to vacancies the member works on |
| moving / rejecting applications | `applications.manage` |
| scheduling interviews | `interviews.schedule`; conducting them `interviews.conduct` |
| notifications sent "to HR managers" | the members holding the permission the event concerns |
| member roles in `Manage-Recruitment-Teams` | `PUT /team/members/{id}/role` with a `roleId` |

A new permission is a new constant in `Permission`; give it to the default roles
that should have it in `SystemRole`, and write a migration if existing Recruiter
or Interviewer rows should gain it.

---

## ADR-10 — Choosing a workspace at sign-in

**Date:** 2026-10-23 · **Status:** Accepted

### Context

The sign-in modal sends only an email and a password; it has no subdomain to
send (ADR-1). Users are unique per `(tenant, email)` and candidates are a
separate identity (ADR-3), so one email can fit several accounts. The old
header-less login signed in to the first company account whose password
matched, in no defined order, and a person whose company and candidate
accounts shared a password could never reach the candidate one.

### Decision

- Without `X-Tenant-Subdomain`, the password is checked against **every**
  account using the email: memberships of any company, a platform
  administrator, the candidate account.
- One match signs in as before. Several answer `409 WORKSPACE_SELECTION_REQUIRED`
  with the choices and a token; `POST /auth/login/select` exchanges the token for
  the chosen account. We did not forbid one email in two companies: consultants
  and people who change jobs exist, and a choice costs one screen.
- **The token** is 32 random bytes, stored as its SHA-256 only
  (`login_selections`, V17), valid for two minutes, single-use (row-locked when
  redeemed), and bound to the accounts the password was checked against — the
  second step never takes a password and never accepts another account. A choice
  refused because the account is unverified or locked does not use it up.
- **Locked accounts are never password-checked on this path.** Otherwise a
  password that fits a locked account would answer differently from one that
  does not, and the lock could be guessed around. A failed attempt counts
  against every account that was checked, since one guess tests them all.
- Disabled accounts are left out entirely.
- Listing the choices leaks nothing: the caller has proven the password for each.

### Consequence

The frontend needs one more step after sign-in (pick a workspace) and must
branch on `code`, not on the message.

---

## ADR-11 — A vacancy is visible to the people who work on it

**Date:** 2026-10-23 · **Status:** Accepted

### Context

With custom roles (ADR-9), "can see vacancies" stopped being all or nothing. An
interviewer needs the vacancies they interview for and no others; a junior
recruiter may run their own adverts without seeing the rest.

### Decision

- `jobs.view.all` sees every vacancy in the company. Without it, a member sees
  the vacancies they **work on**: created (`created_by`, V18), recruit for,
  manage, or are on the hiring team of (`job_vacancy_team_members`, V18).
- Everything else is `404`, for reads and writes — the same rule as across
  tenants, applied inside one. Writes need their permission as well.
- The rule is written once for single lookups (`JobVacancyRepository.WORKS_ON`)
  and once for the list (`VacancySpecifications.worksOn`); `VacancyAccess` is the
  only way recruiter-side code loads one vacancy.
- The controller builds a `VacancyViewer` (tenant, user, sees-all) from the
  token and the current permissions, so services never read security state.
- The hiring team decides visibility only. Members must be active and in the
  vacancy's company; composite foreign keys enforce the company in the database.

Applications should follow the same rule with `applications.view.all`.

---

## ADR-12 — Industries come from a fixed list

**Date:** 2026-10-23 · **Status:** Accepted

### Context

A vacancy should be filed under an industry its company works in, which needs
both to be comparable. The company's industry was free text.

### Decision

- `ProfileTaxonomy.INDUSTRIES` — the list the frontend's forms already show —
  is the only accepted set, stored as those labels and matched loosely
  (`OptionKey`). A company has a main industry and up to three more.
- A vacancy's industry must be one of its company's (`VacancyIndustryPolicy`).
  It defaults to the main one and is kept if the company later drops it, so an
  unrelated edit never fails.
- It is **not required to publish**: requiring it would block every company that
  has not filled in its profile, including through today's frontend.
- V19 rewrote existing free-text values to the list; anything unmatched became
  `Other`. Only API clients could have stored such values — the forms only ever
  offered the list.

---

## ADR-13 — Feature switches are stored, not just shown

**Date:** 2026-10-23 · **Status:** Accepted

The vacancy switches (AI screening, candidate matching — PB-015) and the company
ones (blind screening, and the defaults a new vacancy starts with) are columns
(V20), changed through endpoints where a switch left out keeps its value. A
company default applies only to vacancies created afterwards. Nothing acts on
them yet: the screening, matching and review modules read them when they land.

---

## ADR-14 — Tenant isolation enforced in the ORM, not just by discipline

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
- **Authorization happens twice** for sensitive operations: a permission check
  on the controller (`@RequirePermission`, ADR-9), plus an independent scope
  re-check in the service — tenant, and for vacancies who works on them (ADR-11)
  — so a future caller reaching the service another way cannot bypass it.
- **Clients branch on `code`, never on `message`** (`ErrorCode`). Codes are
  only added, never renamed; messages are sentences and may change.
- **404 over 403 for cross-tenant access:** a resource that exists but belongs
  to another tenant is indistinguishable from one that doesn't exist.
- **Refresh tokens are stored hashed (SHA-256) and rotated on every use**; raw
  tokens exist only in transit and in the client.
- **TenantContext lifecycle:** set only by `TenantResolvingFilter`, cleared in
  its `finally` block; guarded by `TenantContextLeakIntegrationTest`.
