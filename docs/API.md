# API Reference

Base path: `/api/v1`. All bodies are JSON.

## Endpoints

| Method | Path | Auth | Description |
|---|---|---|---|
| POST | `/auth/register` | public | Company onboarding: tenant + first `COMPANY_ADMIN`, created `PENDING_VERIFICATION`. Subdomain optional — generated from the company name when omitted. `409` on duplicate subdomain. |
| POST | `/auth/login` | public | Optional header `X-Tenant-Subdomain` + body `{ email, password }`. No header ⇒ candidate / company / super-admin resolved globally. `401` bad credentials, `403` unverified or locked, `429` rate-limited. |
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
| GET | `/tenant` | `COMPANY_ADMIN`, `HR_MANAGER`, `INTERVIEWER` | Full company profile for the authenticated tenant. Used by dashboard header and Settings page. |
| PATCH | `/tenant` | `COMPANY_ADMIN` | Full-replace semantics despite the verb: every editable field must be sent, and an omitted/`null` field clears it — the client is expected to resubmit the whole profile it fetched from `GET`. Returns the full normalized profile. Immutable fields (`subdomain`, `planTier`, `status`) are ignored. `422` if the tenant is `SUSPENDED`. `400` if `benefits` contains a value outside the fixed option set, or if `name`/`email` is nothing but HTML markup once sanitized. |
| POST | `/tenant/logo` | `COMPANY_ADMIN` | Upload (or replace) company logo. `multipart/form-data`, field `file`. Allowed: `image/png`, `image/jpeg`, `image/svg+xml`, `image/webp`. Max 2 MB. Returns full profile with new `logoUrl`. `422` if the tenant is `SUSPENDED`. |
| POST | `/tenant/cover` | `COMPANY_ADMIN` | Upload (or replace) cover image. Max 4 MB. Returns full profile with new `coverImageUrl`. `422` if the tenant is `SUSPENDED`. |
| DELETE | `/tenant/logo` | `COMPANY_ADMIN` | Remove company logo. Idempotent — `204` even when no logo exists. `422` if the tenant is `SUSPENDED`. |
| DELETE | `/tenant/cover` | `COMPANY_ADMIN` | Remove cover image. Idempotent — `204`. `422` if the tenant is `SUSPENDED`. |
| GET | `/public/companies/{subdomain}` | public | Curated public company profile (name, logo, cover, tagline, description, industry, website, socials, city, country). `404` for unknown subdomain. |
| POST | `/public/candidates/register` | public | Candidate self-registration (no tenant, globally unique email). |
| GET | `/jobs` | `COMPANY_ADMIN`, `HR_MANAGER` | The tenant's vacancies, newest first, paged (`page`, `size` ≤ 100). Every status, **archived included**, unless `status` is given: `DRAFT`, `PUBLISHED`, `CLOSED`, `ARCHIVED`, or `ACTIVE` (everything not archived). `400` for any other value. |
| GET | `/jobs/counts` | `COMPANY_ADMIN`, `HR_MANAGER` | Vacancy totals per status, e.g. `{"DRAFT":3,"PUBLISHED":12,"CLOSED":0,"ARCHIVED":4}`. Always all four keys. |
| GET | `/jobs/{id}` | `COMPANY_ADMIN`, `HR_MANAGER` | One vacancy. `404` if it does not exist **or belongs to another tenant**. |
| POST | `/jobs` | `COMPANY_ADMIN`, `HR_MANAGER` | Create a vacancy as `DRAFT` or straight to `PUBLISHED` (`status` in the body; any other value → `400`). `201`. `422` if published and incomplete. |
| PUT | `/jobs/{id}` | `COMPANY_ADMIN`, `HR_MANAGER` | Replace a vacancy's content. Never changes status. Body carries the `version` being edited: stale → `409`. `422` if the vacancy is closed or archived, or is published and the edit would leave it incomplete. |
| POST | `/jobs/{id}/publish` | `COMPANY_ADMIN`, `HR_MANAGER` | `DRAFT → PUBLISHED`. `422` if not a draft, or not complete (the message lists what is missing). |
| POST | `/jobs/{id}/close` | `COMPANY_ADMIN`, `HR_MANAGER` | `PUBLISHED → CLOSED`. Leaves the public board and stops new applications. `422` if not published. |
| POST | `/jobs/{id}/archive` | `COMPANY_ADMIN`, `HR_MANAGER` | `CLOSED → ARCHIVED`. Leaves the active lists; the record is kept. `422` if not closed. |
| POST | `/jobs/{id}/duplicate` | `COMPANY_ADMIN`, `HR_MANAGER` | Creates a new `DRAFT` pre-filled from this vacancy, from any status. `201` with the new vacancy; the source is unchanged. |
| GET | `/public/jobs` | public | Public job board: `PUBLISHED` vacancies across all companies, paged (`page`, `size` ≤ 50). Optional `q` (keywords), `category`, `location` — see [Public job search](#public-job-search). |
| GET | `/public/jobs/filters` | public | The categories and locations that currently have published vacancies, each with its count — for the board's filter controls. |
| GET | `/public/jobs/{slugOrId}` | public | One published advert in full, by the `slug` from a board card or by id. `404` unless it is currently published. |

### Checkbox option sets

`workModes`, `benefits`, `employmentTypes` and `jobLevels` accept only the values below. Matching folds case, punctuation and `&`/`and`, so `remote_hybrid` and `REMOTE-HYBRID` are the same option; whatever is sent is stored in the canonical spelling shown here, which is what `GET` returns.

`benefits` is the one set expressed as **ids** rather than prose. The label a candidate reads lives in the frontend catalogue, so a perk can be reworded without migrating tenant rows, and the stored value stays filterable.

| Field | Accepted values |
| --- | --- |
| `benefits` | `REMOTE_HYBRID` (Remote / hybrid work), `FLEXIBLE_HOURS` (Flexible working hours), `HEALTH_INSURANCE` (Health insurance), `TRAINING` (Training & development), `PAID_LEAVE` (Generous paid leave), `PARENTAL_LEAVE` (Parental leave), `PERFORMANCE_BONUS` (Performance bonus), `STOCK_OPTIONS` (Stock options), `WELLBEING` (Wellbeing & gym support), `TRANSPORT` (Transport allowance), `MEALS` (Meals provided), `RELOCATION` (Relocation support), `CAREER_DEVELOPMENT` (Career development) |
| `workModes` | `Remote`, `Hybrid`, `On-site` |
| `employmentTypes` | `Full-time`, `Part-time`, `Contract`, `Internship`, `Temporary`, `Freelance` |
| `jobLevels` | `Intern`, `Junior`, `Mid-level`, `Senior`, `Lead`, `Manager`, `Director` |

The source of truth is `ProfileTaxonomy`; `ProfileTaxonomyDriftTest` fails the build if this set and the DTO's `@AllowedValues` drift apart.

### Vacancy lifecycle

```
DRAFT ──publish──▶ PUBLISHED ──close──▶ CLOSED ──archive──▶ ARCHIVED
```

One-way: there is no reopen, unpublish or unarchive, and no delete. Any other
move is a `422` whose `message` names the rule and the vacancy's real state, e.g.
*"Only a published vacancy can be closed. This vacancy is a draft."*

| Status | Dashboard | Public board | New applications | Editable (`PUT`) |
| --- | --- | --- | --- | --- |
| `DRAFT` | listed | hidden (`404`) | — | yes; may be incomplete |
| `PUBLISHED` | listed | **listed** | accepted until the deadline day ends | yes; must stay complete |
| `CLOSED` | listed | hidden (`404`) | refused | no (`422`) |
| `ARCHIVED` | only with no `status` filter or `status=ARCHIVED` | hidden (`404`) | refused | no (`422`) |

- **Complete** (required to be `PUBLISHED`): `title`, `department`, `location`,
  `employmentType`, `workplaceType`, `jobSummary`, `jobDescription`, at least one
  `keyResponsibilities` and one `requiredSkills`, and an `applicationDeadline` on
  or after today **in the tenant's timezone** (UTC if the profile has none).
  Failing this is a `422` with one sentence listing everything missing.
- **Shape rules** (lengths, ranges, enum values) apply in every state and are `400`.
  **Cross-field rules** are `422`: salary maximum below minimum; a salary without
  `currency` and `payPeriod`; an unknown `recruitmentPipelineId`; an
  `assignedRecruiterId` / `hiringManagerId` that is not an active user of the tenant.
- `message` on `400`/`422` from these endpoints is written for the recruiter and
  is safe to show as is.
- Every write returns the vacancy **as now stored**, with `publishedAt` /
  `closedAt` / `archivedAt` set on entering each state and never cleared.
  `version` is the optimistic-lock counter to send back on `PUT`.
  `applicantCount` is `null` until the applications module exists. `tenantId` is
  never in a response.
- All free text is HTML-stripped before storage (the advert is rendered publicly).
- **Duplicate** copies the content, appends ` (copy)` to the title, clears the
  deadline, and keeps an assignee only if they are still an active member.
- **Applications**: other modules must not read a vacancy's status themselves.
  The job module exposes `VacancyApplicationGate.requireOpen(vacancyId)`, to be
  called inside the transaction that stores an application.

### Public job search

`GET /public/jobs` — every parameter is optional; they combine with AND.

| Param | Matches | Notes |
| --- | --- | --- |
| `q` | title, required and preferred skills, department, summary, location, description, responsibilities | Full-text, ranked: a hit in the title outranks one in the body. Word forms match (`engineers` finds *Engineer*). Supports `"exact phrase"`, `OR`, and `-exclude`. Any input is safe — it can never produce an error. |
| `category` | the vacancy's `department`, whole value | Case-insensitive. Use a value from `/public/jobs/filters`. |
| `location` | anywhere in the vacancy's `location` | Case-insensitive: `colombo` finds *Colombo, Sri Lanka*. |
| `page`, `size` | — | `size` is capped at 50; out-of-range values are clamped, not rejected. |

Ordered by relevance when `q` is given, otherwise newest first. Each card carries
`slug` (use it in the advert's URL; it keeps resolving after a title edit) and
`acceptingApplications` (`false` once the deadline has passed). Cards and adverts
never include tenant ids, status, version, assignees or screening questions.

Results are read live on every request — a publish, edit or close is visible on
the very next call.

## Error model

Every failure returns the uniform envelope:

```json
{ "timestamp": "...", "status": 403, "error": "Forbidden",
  "message": "Account temporarily locked. Try again in 12 minute(s).",
  "path": "/api/v1/auth/login" }
```

Status semantics: `400` validation · `401` bad/expired token or bad credentials ·
`403` wrong role / unverified / locked · `404` missing **or cross-tenant** ·
`409` conflict · `422` business rule · `429` rate-limited · `500` unhandled
(client gets an opaque correlation id; the stack trace stays in the logs).

## Security behavior

- **Account lockout**: 5 consecutive failed logins lock an account for 15 minutes
  (`403` with retry timing). Applies to company users and candidates.
- **Login rate limit**: 10 requests/minute per IP on `/auth/login`,
  `/auth/forgot-password`, `/auth/resend-verification` → `429` + `Retry-After`.
- **Tenant identity** comes only from the JWT claim or the `X-Tenant-Subdomain`
  header — never from a request body or path parameter.
- **Anti-enumeration**: identical generic `401` for unknown tenant/email/wrong
  password; always-`200` on forgot-password and resend-verification; `404` (not
  `403`) for cross-tenant resources.
- **Company profile free-text fields** (`name`, `tagline`, `description`,
  `culture`, `mission`, `vision`, `legalName`, address fields, and every
  tag-input list) are HTML-stripped server-side before persistence — plain
  text in, plain text out, no entity-encoding. URL fields (`website`,
  `linkedinUrl`, …) accept only `http`/`https` schemes; anything else
  (`javascript:`, `data:`, `file:`, …) is rejected with `400`.

## Email-linked flows (verification, reset, invitations)

The links for these flows are delivered by email. In the default **console
mode** (no `RESEND_API_KEY` set) nothing is sent — every link is written to the
backend log prefixed `[EMAIL-CONSOLE]`; copy it into the browser. With a Resend
key set, mail is sent asynchronously (see `infra/.env.example` for the
deliverability caveats and required variables).

End-to-end local walkthrough (console mode):

1. Register a company (`POST /auth/register`).
2. Copy the `/verify-email?token=…` link from the backend log → verify.
3. Log in (`POST /auth/login`) with the `X-Tenant-Subdomain` header.
4. Invite an HR manager (`POST /team/invitations`) → accept via the logged
   `/accept-invite?token=…` link → log in.
5. Register a candidate (`POST /public/candidates/register`) → verify → log in
   with no tenant header.
6. `POST /auth/forgot-password` → confirm via the logged `/reset-password?token=…`
   link (all existing sessions are revoked).
