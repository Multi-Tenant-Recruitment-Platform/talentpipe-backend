# Frontend changes for the roles, sign-in and vacancy work

What `talentpipe-frontend` needs to change to use the backend from
`feature/review-and-refine-existing-features`. The API itself is in
[API.md](API.md); the reasoning is in ADR-9 to ADR-13 of [DECISIONS.md](DECISIONS.md).
Nothing here breaks today's frontend — every item is something it can start
using.

## Sign-in and sign-up modals

- **Pick a workspace.** `POST /auth/login` without `X-Tenant-Subdomain` can now
  answer `409` with `code: "WORKSPACE_SELECTION_REQUIRED"`. Show
  `details.accounts` (company name, logo and role; or "Candidate"), then send
  `POST /auth/login/select { selectionToken, accountId }`. It returns the same
  body as a normal login. The token lasts `details.expiresInSeconds` (120); on
  `401 INVALID_TOKEN` go back to the sign-in form.
- **Branch on `code`, not on the message.** Replace the search for
  "not verified" in the message:
  - `ACCOUNT_NOT_VERIFIED` → offer "resend verification email";
  - `INVITATION_NOT_ACCEPTED` → "use the link in your invitation email";
  - `ACCOUNT_LOCKED` → show `message` (it has the retry time);
  - `INVALID_CREDENTIALS` → the usual wrong email or password.
- **Field errors.** Every `400 VALIDATION_FAILED` has
  `fieldErrors: [{ field, message }]` (e.g. `admin.password`). Show each message
  under its input; the messages are written for people.
- **NIC.** Candidate sign-up accepts Sri Lankan NICs only: 9 digits + `V`/`X`
  (old) or 12 digits (new), with a valid birth-day number. Spaces and dashes are
  dropped, and the field takes at most 20 characters as typed — the input's
  `maxLength` is 30, so lower it to 20. A hint such as "e.g. 200012345678 or
  912345678V" matches the backend's own message.

## Permissions instead of roles

- Login and `/auth/me` return `user.permissions` (keys such as `jobs.manage`),
  `roleId` and `roleName`. Replace the hard-coded matrix in
  `src/auth/permissions.ts` with `permissions.includes(key)`. `role` stays for
  now, but is `CUSTOM` for a custom role, so it cannot drive the UI.
- A permission change applies on the user's next request, so re-fetch `/auth/me`
  after a `403` before deciding what to hide.
- New screens this enables: roles list and editor (`GET /permissions` gives the
  groups, labels and descriptions; `/roles` CRUD; `GET /roles/audit`), and a role
  picker per member (`PUT /team/members/{id}/role`). Invitations take
  `roleId` from `GET /roles`. The old `role` field still works.
- `409 CONCURRENT_UPDATE` on a role edit means someone else saved first: reload
  the role, then retry.

## Vacancies (dashboard)

- `GET /jobs` is now open to everyone in the company and already narrowed to
  what the caller may see. An interviewer gets their vacancies, not a `403`.
  Add `assignedTo=me` for a "My vacancies" tab and `department=` for a filter.
- Hiring team panel: `GET /jobs/{id}/hiring-team`, and
  `PUT … { memberIds }` to save it (shown when the user has `jobs.manage`).
- Vacancy form: `industry` as a select of the company's industries (`industry`
  plus `additionalIndustries` from `GET /tenant`). Leaving it out uses the main
  one. Show `createdBy`.
- AI switches (the old `VacancyFeatureSettings`): read `aiScreeningEnabled` /
  `candidateMatchingEnabled` from the vacancy; save with
  `PATCH /jobs/{id}/features`, sending only the switch that changed.

## Company settings

- Industry: `industry` must come from `INDUSTRIES` (already the case);
  `additionalIndustries` takes up to 3 more from the same list.
- Switches page: `GET` / `PATCH /tenant/settings` for `blindScreeningEnabled`,
  `aiScreeningDefault`, `candidateMatchingDefault`, sending only what changed.

## Public job board

The filters in `src/jobs/jobFilters.ts` run over the loaded page only. Send them
to `GET /public/jobs` instead, so totals and pages are right:

| `JobFilters` | Query parameter |
| --- | --- |
| `keyword`, `location` | `q`, `location` (unchanged) |
| `employmentTypes`, `workplaceTypes` | `employmentType`, `workplaceType` (comma-separated) |
| `experience: none / upTo2 / upTo5 / fivePlus` | `maxExperience=0` / `maxExperience=2` / `maxExperience=5` / `minExperience=5` |
| `salaryOnly` | `salaryOnly=true` |
| `payPeriod`, `salaryRange` | `payPeriod`, `currency` (e.g. `LKR`), `salaryMin`, `salaryMax` |
| `sort` | `sort=relevant` or `sort=newest` |
| — (new) | `industry` |

`GET /public/jobs/filters` now also returns `industries`, `employmentTypes`,
`workplaceTypes` (each with counts) and `salaryRanges`
(`{ payPeriod, currency, min, max, count }`). That is what `salaryBounds()`
computes today from the loaded page.
