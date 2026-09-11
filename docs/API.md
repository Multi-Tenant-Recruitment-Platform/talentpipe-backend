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
| GET | `/public/jobs` | public | Public job board — empty page until the Job module lands. |

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
