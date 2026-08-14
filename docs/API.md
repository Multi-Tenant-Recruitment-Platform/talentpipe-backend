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
| PATCH | `/tenant` | `COMPANY_ADMIN` | Update editable profile fields. Returns full normalized profile. Immutable fields (`subdomain`, `planTier`, `status`) are ignored. |
| POST | `/tenant/logo` | `COMPANY_ADMIN` | Upload (or replace) company logo. `multipart/form-data`, field `file`. Allowed: `image/png`, `image/jpeg`, `image/svg+xml`, `image/webp`. Max 2 MB. Returns full profile with new `logoUrl`. |
| POST | `/tenant/cover` | `COMPANY_ADMIN` | Upload (or replace) cover image. Max 4 MB. Returns full profile with new `coverImageUrl`. |
| DELETE | `/tenant/logo` | `COMPANY_ADMIN` | Remove company logo. Idempotent — `204` even when no logo exists. |
| DELETE | `/tenant/cover` | `COMPANY_ADMIN` | Remove cover image. Idempotent — `204`. |
| GET | `/public/companies/{subdomain}` | public | Curated public company profile (name, logo, cover, tagline, description, industry, website, socials, city, country). `404` for unknown subdomain. |
| POST | `/public/candidates/register` | public | Candidate self-registration (no tenant, globally unique email). |
| GET | `/public/jobs` | public | Public job board — empty page until the Job module lands. |

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
