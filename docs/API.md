# API Reference

Base path: `/api/v1`. All bodies are JSON.

## Endpoints

**Auth** column: `public` needs no token; `bearer` needs any valid token; `member`
needs a token of an active company user, whatever their role; a key such as
`jobs.manage` needs that permission (see [Roles and permissions](#roles-and-permissions)).

| Method | Path | Auth | Description |
|---|---|---|---|
| POST | `/auth/register` | public | Company onboarding: tenant + its first **Owner**, created `PENDING_VERIFICATION`, with the three default roles. Subdomain optional — generated from the company name when omitted. `409` on duplicate subdomain. |
| POST | `/auth/login` | public | Optional header `X-Tenant-Subdomain` + body `{ email, password }`. Without the header the credentials are tried against every account using the email — see [Signing in without a workspace](#signing-in-without-a-workspace). `401` bad credentials, `403` unverified / invitation not accepted / locked, `409` several accounts match, `429` rate-limited. |
| POST | `/auth/login/select` | public | Second step after a `409 WORKSPACE_SELECTION_REQUIRED`: `{ selectionToken, accountId }` → the same response as `/auth/login`. `401 INVALID_TOKEN` unknown, used or expired token; `400` an account that was not offered; `403` the chosen account is not verified / locked (the token stays usable for another choice). Rate-limited like `/auth/login`. |
| POST | `/auth/verify-email` | public | Consumes a verification token (user *or* candidate) and activates the account. |
| POST | `/auth/resend-verification` | public | New verification link. Always `200` — never reveals whether the address exists. |
| POST | `/auth/refresh` | public | Rotates a refresh token → new pair. Replay of a consumed token → `401`. |
| POST | `/auth/logout` | bearer | Revokes the presented refresh token (idempotent). |
| GET | `/auth/me` | bearer | Current principal's profile (user or candidate), including `roleId`, `roleName` and `permissions`. |
| POST | `/auth/forgot-password` | public | Starts a reset. Always `200`. 30-minute single-use link per matching account. |
| POST | `/auth/password-reset/confirm` | public | Sets a new password and revokes every active session. |
| POST | `/auth/accept-invite` | public | Sets the first password and activates an invited account. |
| GET | `/permissions` | member | The permission catalogue, grouped, with a label and description for each — what a role editor lists. |
| GET | `/roles` | `team.view` | The workspace's roles: Owner, Recruiter, Interviewer first, then custom roles by name; each with its permissions and `memberCount`. |
| GET | `/roles/{id}` | `team.view` | One role. `404` if not in the caller's workspace. |
| POST | `/roles` | `roles.manage` | Creates a custom role `{ name, description?, permissions[] }`. `409` name taken; `422` a permission the caller does not hold, or more than 50 custom roles. |
| PUT | `/roles/{id}` | `roles.manage` | Replaces a role's name, description and permissions. Needs the `version` it was based on: stale → `409 CONCURRENT_UPDATE`. `422` for the Owner role, or granting/revoking what the caller does not hold. Takes effect on the members' next request. |
| POST | `/roles/{id}/duplicate` | `roles.manage` | Copies a role as "Name (copy)". `201`. |
| DELETE | `/roles/{id}?reassignTo={roleId}` | `roles.manage` | Deletes a custom role. Default roles → `422`. A role with members needs `reassignTo`, else `422` naming how many people have it. |
| GET | `/roles/audit` | `roles.manage` | Who changed which role or member, newest first, paged (`size` ≤ 100). |
| GET | `/team` | `team.view` | Members and pending invitations in the caller's tenant, each with `roleId` and `roleName`. |
| POST | `/team/invitations` | `team.invite` | Invites a person into `roleId` (any role but Owner), or into the old `role` `HR_MANAGER` / `INTERVIEWER`. `409` if the email already exists here; `422` a role whose permissions the caller does not hold. |
| POST | `/team/invitations/{userId}/resend` | `team.invite` | Re-sends an invitation. `404` if it isn't in the caller's tenant. |
| DELETE | `/team/invitations/{userId}` | `team.invite` | Revokes a pending invitation. |
| PUT | `/team/members/{userId}/role` | `team.manage` | Moves a member to `{ roleId }`. Only an Owner makes someone an Owner or moves an Owner; the last active Owner cannot be moved (`422`). |
| GET | `/tenant` | member | Full company profile for the authenticated tenant. Used by dashboard header and Settings page. |
| PATCH | `/tenant` | `settings.edit` | Full-replace semantics despite the verb: every editable field must be sent, and an omitted/`null` field clears it — the client is expected to resubmit the whole profile it fetched from `GET`. Returns the full normalized profile. Immutable fields (`subdomain`, `planTier`, `status`) are ignored. `422` if the tenant is `SUSPENDED`. `400` if `benefits`, `industry` or `additionalIndustries` holds a value outside its fixed option set, if `additionalIndustries` has more than 3 entries or is given without an `industry`, or if `name`/`email` is nothing but HTML markup once sanitized. |
| GET | `/tenant/settings` | member | The company's switches: `blindScreeningEnabled`, `aiScreeningDefault`, `candidateMatchingDefault`. |
| PATCH | `/tenant/settings` | `settings.edit` | Changes the switches given; one left out keeps its value. `422` if the tenant is `SUSPENDED`. |
| POST | `/tenant/logo` | `settings.edit` | Upload (or replace) company logo. `multipart/form-data`, field `file`. Allowed: `image/png`, `image/jpeg`, `image/svg+xml`, `image/webp`. Max 2 MB. Returns full profile with new `logoUrl`. `422` if the tenant is `SUSPENDED`. |
| POST | `/tenant/cover` | `settings.edit` | Upload (or replace) cover image. Max 4 MB. Returns full profile with new `coverImageUrl`. `422` if the tenant is `SUSPENDED`. |
| DELETE | `/tenant/logo` | `settings.edit` | Remove company logo. Idempotent — `204` even when no logo exists. `422` if the tenant is `SUSPENDED`. |
| DELETE | `/tenant/cover` | `settings.edit` | Remove cover image. Idempotent — `204`. `422` if the tenant is `SUSPENDED`. |
| GET | `/public/companies/{subdomain}` | public | Curated public company profile (name, logo, cover, tagline, description, industry and additional industries, website, socials, city, country). `404` for unknown subdomain. |
| POST | `/public/candidates/register` | public | Candidate self-registration (no tenant, globally unique email). Details in [CANDIDATE_API.md](CANDIDATE_API.md). |
| GET | `/candidates/me` | `CANDIDATE` | Current candidate's full profile and active CV metadata. Details in [CANDIDATE_API.md](CANDIDATE_API.md). |
| PUT | `/candidates/me` | `CANDIDATE` | Updates candidate details, photo, talent pool status, and versioned CV (`multipart/form-data`). |
| GET | `/candidates/me/cv/download` | `CANDIDATE` | Downloads the candidate's active CV as a file attachment. |
| GET | `/public/media/candidate/{candidateId}/photos/{filename}` | public | Publicly streams the candidate's avatar image. |
| GET | `/jobs` | member | The vacancies the caller may see (see [Who sees which vacancy](#who-sees-which-vacancy)), newest first, paged (`page`, `size` ≤ 100). Every status, **archived included**, unless `status` is given: `DRAFT`, `PUBLISHED`, `CLOSED`, `ARCHIVED`, or `ACTIVE` (everything not archived). Also `assignedTo=me` or a member's id (recruiter, hiring manager or hiring team), and `department` (whole value, ignoring case). `400` for any other `status` or `assignedTo`. |
| GET | `/jobs/counts` | member | Totals per status of the vacancies the caller may see, e.g. `{"DRAFT":3,"PUBLISHED":12,"CLOSED":0,"ARCHIVED":4}`. Always all four keys. |
| GET | `/jobs/{id}` | member | One vacancy. `404` if it does not exist, **belongs to another tenant**, or is not the caller's to see. |
| GET | `/jobs/{id}/hiring-team` | member | Who works on the vacancy: `recruiter`, `hiringManager` and the rest of the team as `members[]`, each `{ id, firstName, lastName, email, roleName, status }`. |
| PUT | `/jobs/{id}/hiring-team` | `jobs.manage` | Replaces the team: `{ memberIds: [] }` (≤ 50; repeats ignored). Newcomers must be active members of the workspace (`422`). `422` once the vacancy is archived. |
| POST | `/jobs` | `jobs.manage` | Create a vacancy as `DRAFT` or straight to `PUBLISHED` (`status` in the body; any other value → `400`). `201`. `422` if published and incomplete, or `industry` is not one of the company's. The caller is recorded as `createdBy`. |
| PUT | `/jobs/{id}` | `jobs.manage` | Replace a vacancy's content. Never changes status. Body carries the `version` being edited: stale → `409`. `422` if the vacancy is closed or archived, or is published and the edit would leave it incomplete. `industry`, `aiScreeningEnabled` and `candidateMatchingEnabled` left out keep their values. |
| PATCH | `/jobs/{id}/features` | `jobs.manage` | `{ aiScreeningEnabled?, candidateMatchingEnabled? }` — a switch left out keeps its value. Returns the vacancy. `422` once closed or archived. |
| POST | `/jobs/{id}/publish` | `jobs.publish` | `DRAFT → PUBLISHED`. `422` if not a draft, or not complete (the message lists what is missing). |
| POST | `/jobs/{id}/close` | `jobs.publish` | `PUBLISHED → CLOSED`. Leaves the public board and stops new applications. `422` if not published. |
| POST | `/jobs/{id}/archive` | `jobs.publish` | `CLOSED → ARCHIVED`. Leaves the active lists; the record is kept. `422` if not closed. |
| POST | `/jobs/{id}/duplicate` | `jobs.manage` | Creates a new `DRAFT` pre-filled from this vacancy, from any status, with its hiring team and switches. `201` with the new vacancy; the source is unchanged. |
| GET | `/public/jobs` | public | Public job board: `PUBLISHED` vacancies across all companies, paged (`page`, `size` ≤ 50), filtered on the server — see [Public job search](#public-job-search). |
| GET | `/public/jobs/filters` | public | Everything the board can be filtered by right now, each with its count: categories, locations, industries, employment and workplace types, and the span of stated pay per pay period and currency. |
| GET | `/public/jobs/{slugOrId}` | public | One published advert in full, by the `slug` from a board card or by id. `404` unless it is currently published. |

Every endpoint must carry an authorization annotation; `EndpointAuthorizationDriftTest`
fails the build otherwise.

### Roles and permissions

A role is a named set of permissions. Every workspace starts with three default
roles; anyone with `roles.manage` can add custom ones. Endpoints check
permissions, never role names.

| Group | Key | What it allows | Owner | Recruiter | Interviewer |
| --- | --- | --- | :-: | :-: | :-: |
| Team | `team.view` | See members and pending invitations | ✓ | | |
| Team | `team.invite` | Invite, re-send, revoke (needs `team.view`) | ✓ | | |
| Team | `team.manage` | Change a member's role (needs `team.view`) | ✓ | | |
| Team | `roles.manage` | Create, edit, delete roles (needs `team.view`) | ✓ | | |
| Company | `settings.edit` | Company profile, logo, cover, settings | ✓ | | |
| Vacancies | `jobs.view.all` | See every vacancy (otherwise only the ones you work on) | ✓ | ✓ | |
| Vacancies | `jobs.manage` | Create, edit, duplicate, staff, switch features | ✓ | ✓ | |
| Vacancies | `jobs.publish` | Publish, close, archive (needs `jobs.manage`) | ✓ | ✓ | |
| Hiring | `pipeline.templates.manage` | Pipeline templates | ✓ | | |
| Hiring | `applications.view.all` | See applicants to every vacancy | ✓ | ✓ | |
| Hiring | `applications.manage` | Move and reject applicants | ✓ | ✓ | |
| Interviews | `interviews.schedule` | Schedule interviews, pick interviewers, add the meeting link | ✓ | ✓ | |
| Interviews | `interviews.conduct` | Join interviews you are invited to, submit feedback | ✓ | | ✓ |

- **Owner** always holds every permission and cannot be edited or deleted.
  Recruiter and Interviewer can be edited but not deleted.
- Guardrails (`422`): nobody grants, or takes away, a permission they do not hold
  themselves; only an Owner makes someone an Owner or moves an Owner; a workspace
  always keeps one active Owner; a role in use is deleted only with `reassignTo`.
  Every change is recorded (`GET /roles/audit`).
- A change applies on the affected members' **next request** — permissions are
  resolved per request, not read from the token.
- Login and `/auth/me` return `role` (the old name: `COMPANY_ADMIN`,
  `HR_MANAGER`, `INTERVIEWER`, or `CUSTOM` for a custom role — kept for older
  clients), `roleId`, `roleName` and `permissions` (keys, in catalogue order).
  Show and hide UI by `permissions`.

### Signing in without a workspace

The sign-in modal sends only `{ email, password }`. One email can belong to
several accounts — the same person in two companies, or a company member who is
also a candidate — so the password is checked against all of them:

- **One match** — signed in, as before.
- **Several matches** — `409` with `code: "WORKSPACE_SELECTION_REQUIRED"` and:

  ```json
  "details": {
    "selectionToken": "…", "expiresInSeconds": 120,
    "accounts": [
      { "accountId": "…", "type": "COMPANY", "workspaceName": "Acme", "subdomain": "acme",
        "logoUrl": "…", "roleName": "Recruiter" },
      { "accountId": "…", "type": "CANDIDATE", "roleName": "Candidate" }
    ]
  }
  ```

  Companies come first by name, then a `PLATFORM` account, then the candidate
  one. Show the list; send the choice to `POST /auth/login/select`. The token is
  single-use and lasts two minutes; nothing is signed in until it is redeemed.
- **No match** — the usual `401`, and every account checked counts a failed
  attempt. Locked accounts are never password-checked on this path; when every
  account is locked the answer is `403 ACCOUNT_LOCKED` with the earliest unlock.

Sending `X-Tenant-Subdomain` skips all of this and signs in to that workspace.

### Who sees which vacancy

- With `jobs.view.all`: every vacancy in the company.
- Without it: only the vacancies the person **works on** — ones they created
  (`createdBy`), recruit for (`assignedRecruiterId`), manage (`hiringManagerId`) or
  are on the hiring team of (`/jobs/{id}/hiring-team`).
- Every other vacancy answers `404`, for reads and writes alike. Writes still need
  their own permission on top.
- The hiring team decides visibility only; what a member may do is their role's.

### Industries

`industry` (the main one) and `additionalIndustries` (up to 3 more) take values
from this list, matched like the checkbox sets below and stored as shown:

`Information Technology`, `Finance & Banking`, `Healthcare`, `Education`,
`Retail & E-commerce`, `Manufacturing`, `Hospitality & Leisure`,
`Construction & Engineering`, `Logistics & Transport`, `Other`.

A vacancy's `industry` must be one of its company's. Left out, a new vacancy takes
the company's main industry and an edit keeps the current one; a value the
company does not list is `422` naming the ones it does. A company with no
industry can still publish — its vacancies have none. A vacancy keeps its
industry if the company later drops it.

### Feature switches

| Where | Field | Effect |
| --- | --- | --- |
| Vacancy | `aiScreeningEnabled` | Score incoming applications against the vacancy (PB-015). |
| Vacancy | `candidateMatchingEnabled` | Suggest talent-pool candidates who fit it (PB-015). |
| Company | `blindScreeningEnabled` | Reviewers see applications without who sent them. |
| Company | `aiScreeningDefault`, `candidateMatchingDefault` | What a **new** vacancy starts with; existing vacancies keep their own. |

All start off. They are stored only — the screening and matching that read them
arrive with their modules.

### Checkbox option sets

`workModes`, `benefits`, `employmentTypes` and `jobLevels` accept only the values below. Matching folds case, punctuation and `&`/`and`, so `remote_hybrid` and `REMOTE-HYBRID` are the same option; whatever is sent is stored in the canonical spelling shown here, which is what `GET` returns.

`benefits` is the one set expressed as **ids** rather than prose. The label a candidate reads lives in the frontend catalogue, so a perk can be reworded without migrating tenant rows, and the stored value stays filterable.

| Field | Accepted values |
| --- | --- |
| `benefits` | `REMOTE_HYBRID` (Remote / hybrid work), `FLEXIBLE_HOURS` (Flexible working hours), `HEALTH_INSURANCE` (Health insurance), `TRAINING` (Training & development), `PAID_LEAVE` (Generous paid leave), `PARENTAL_LEAVE` (Parental leave), `PERFORMANCE_BONUS` (Performance bonus), `STOCK_OPTIONS` (Stock options), `WELLBEING` (Wellbeing & gym support), `TRANSPORT` (Transport allowance), `MEALS` (Meals provided), `RELOCATION` (Relocation support), `CAREER_DEVELOPMENT` (Career development) |
| `workModes` | `Remote`, `Hybrid`, `On-site` |
| `employmentTypes` | `Full-time`, `Part-time`, `Contract`, `Internship`, `Temporary`, `Freelance` |
| `jobLevels` | `Intern`, `Junior`, `Mid-level`, `Senior`, `Lead`, `Manager`, `Director` |

The source of truth is `ProfileTaxonomy`; `ProfileTaxonomyDriftTest` fails the build if this set (and the industry list) and the DTO's `@AllowedValues` drift apart.

### Vacancy lifecycle

```
DRAFT ──publish──▶ PUBLISHED ──close──▶ CLOSED ──archive──▶ ARCHIVED
```

One-way: there is no reopen, unpublish or unarchive, and no delete. Any other
move is a `422` whose `message` names the rule and the vacancy's real state, e.g.
*"Only a published vacancy can be closed. This vacancy is a draft."*

| Status | Dashboard | Public board | New applications | Editable (`PUT`, features) | Hiring team |
| --- | --- | --- | --- | --- | --- |
| `DRAFT` | listed | hidden (`404`) | — | yes; may be incomplete | yes |
| `PUBLISHED` | listed | **listed** | accepted until the deadline day ends | yes; must stay complete | yes |
| `CLOSED` | listed | hidden (`404`) | refused | no (`422`) | yes |
| `ARCHIVED` | only with no `status` filter or `status=ARCHIVED` | hidden (`404`) | refused | no (`422`) | no (`422`) |

- **Complete** (required to be `PUBLISHED`): `title`, `department`, `location`,
  `employmentType`, `workplaceType`, `jobSummary`, `jobDescription`, at least one
  `keyResponsibilities` and one `requiredSkills`, and an `applicationDeadline` on
  or after today **in the tenant's timezone** (UTC if the profile has none).
  Failing this is a `422` with one sentence listing everything missing.
- **Shape rules** (lengths, ranges, enum values) apply in every state and are `400`.
  **Cross-field rules** are `422`: salary maximum below minimum; a salary without
  `currency` and `payPeriod`; an unknown `recruitmentPipelineId`; an
  `assignedRecruiterId` / `hiringManagerId` that is not an active user of the
  tenant; an `industry` the company does not list.
- `message` on `400`/`422` from these endpoints is written for the recruiter and
  is safe to show as is.
- Every write returns the vacancy **as now stored**, with `publishedAt` /
  `closedAt` / `archivedAt` set on entering each state and never cleared.
  `version` is the optimistic-lock counter to send back on `PUT`.
  `applicantCount` is `null` until the applications module exists. `createdBy` is
  `null` for vacancies filed before it was recorded. `tenantId` is never in a
  response.
- All free text is HTML-stripped before storage (the advert is rendered publicly).
- **Duplicate** copies the content, appends ` (copy)` to the title, clears the
  deadline, and keeps an assignee or hiring-team member only if they are still an
  active member. It keeps the source's industry while the company still lists it.
- **Applications**: other modules must not read a vacancy's status themselves.
  The job module exposes `VacancyApplicationGate.requireOpen(vacancyId)`, to be
  called inside the transaction that stores an application.

### Public job search

`GET /public/jobs` — every parameter is optional; they combine with AND. All of
them run on the server over every published vacancy, so totals and pages are
right whatever is filtered.

| Param | Matches | Notes |
| --- | --- | --- |
| `q` | title, required and preferred skills, department, summary, location, description, responsibilities | Full-text, ranked: a hit in the title outranks one in the body. Word forms match (`engineers` finds *Engineer*). Supports `"exact phrase"`, `OR`, and `-exclude`. Any input is safe — it can never produce an error. |
| `category` | the vacancy's `department`, whole value | Case-insensitive. Use a value from `/public/jobs/filters`. |
| `location` | anywhere in the vacancy's `location` | Case-insensitive: `colombo` finds *Colombo, Sri Lanka*. |
| `industry` | the vacancy's industry | One of the [listed industries](#industries), spelling forgiven; any other value is `400`. |
| `employmentType` | any of the values | `FULL_TIME`, `PART_TIME`, `CONTRACT`, `INTERNSHIP`, `TEMPORARY`. Repeat the parameter or separate with commas. |
| `workplaceType` | any of the values | `ON_SITE`, `REMOTE`, `HYBRID`. |
| `minExperience`, `maxExperience` | years the vacancy asks for | 0–50. A vacancy that names none asks for none (0). |
| `salaryOnly` | vacancies that state their pay | `true` / `false`. |
| `payPeriod`, `currency`, `salaryMin`, `salaryMax` | stated pay overlapping the range | `HOURLY` / `MONTHLY` / `ANNUAL`; `currency` is a code such as `LKR`. A range needs a `payPeriod` — pay in different periods does not compare (`400`). A one-sided salary is a range of one point. |
| `sort` | — | `relevant` (default: best `q` match first) or `newest`. |
| `page`, `size` | — | `size` is capped at 50; out-of-range values are clamped, not rejected. |

Without `q`, or with `sort=newest`, results are newest first. Each card carries
`slug` (use it in the advert's URL; it keeps resolving after a title edit),
`industry`, and `acceptingApplications` (`false` once the deadline has passed).
Cards and adverts never include tenant ids, status, version, assignees or
screening questions.

Results are read live on every request — a publish, edit or close is visible on
the very next call.

## Error model

Every failure returns the uniform envelope:

```json
{ "timestamp": "...", "status": 400, "error": "Bad Request",
  "code": "VALIDATION_FAILED",
  "message": "admin.password: Use a password of 8 to 72 characters.",
  "path": "/api/v1/auth/register",
  "fieldErrors": [ { "field": "admin.password", "message": "Use a password of 8 to 72 characters." } ] }
```

- `code` is the contract: branch on it, never on `message`. Codes are only ever
  added, never renamed.
- `message` is a sentence for people and may be reworded.
- `fieldErrors` appears only on `VALIDATION_FAILED` — one entry per invalid field,
  to show next to its input. `details` appears only for codes that carry a
  payload (today, `WORKSPACE_SELECTION_REQUIRED`).

| Status | Codes |
| --- | --- |
| 400 | `VALIDATION_FAILED`, `INVALID_REQUEST`, `MALFORMED_REQUEST`, `INVALID_PARAMETER`, `MISSING_HEADER` |
| 401 | `INVALID_CREDENTIALS`, `AUTHENTICATION_REQUIRED`, `INVALID_TOKEN` |
| 403 | `ACCESS_DENIED`, `ACCOUNT_NOT_VERIFIED` (offer a new verification email), `INVITATION_NOT_ACCEPTED` (point to the invitation email), `ACCOUNT_LOCKED` |
| 404 | `NOT_FOUND` — missing **or cross-tenant**, indistinguishable on purpose |
| 405 | `METHOD_NOT_ALLOWED` |
| 409 | `CONFLICT`, `CONCURRENT_UPDATE` (reload, then retry), `WORKSPACE_SELECTION_REQUIRED` |
| 413 / 415 | `PAYLOAD_TOO_LARGE`, `UNSUPPORTED_MEDIA_TYPE` |
| 422 | `BUSINESS_RULE` |
| 429 | `RATE_LIMITED` |
| 500 | `INTERNAL_ERROR` — the message carries an opaque correlation id; the stack trace stays in the logs |

## Security behavior

- **Account lockout**: 5 consecutive failed logins lock an account for 15 minutes
  (`403` with retry timing). Applies to company users and candidates.
- **Login rate limit**: 10 requests/minute per IP on `/auth/login`,
  `/auth/login/select`, `/auth/forgot-password`, `/auth/resend-verification` →
  `429` + `Retry-After`.
- **Tenant identity** comes only from the JWT claim or the `X-Tenant-Subdomain`
  header — never from a request body or path parameter.
- **Permissions** are resolved on every request from the member's current role
  (cached for at most a minute, and dropped at once when a role or member
  changes), so a token issued before a change never carries stale rights.
- **Anti-enumeration**: identical generic `401` for unknown tenant/email/wrong
  password; always-`200` on forgot-password and resend-verification; `404` (not
  `403`) for cross-tenant resources and for vacancies a member may not see.
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
4. Invite someone (`POST /team/invitations` with a `roleId` from `GET /roles`) →
   accept via the logged `/accept-invite?token=…` link → log in.
5. Register a candidate (`POST /public/candidates/register`) → verify → log in
   with no tenant header.
6. `POST /auth/forgot-password` → confirm via the logged `/reset-password?token=…`
   link (all existing sessions are revoked).
