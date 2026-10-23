# Demo data

A ready-made, realistic dataset for presentations: three companies, their
hiring teams, 22 vacancies in every state, pipeline templates, 11 candidates
and 24 applications. Loaded by `DemoDataSeeder`, only when the backend runs
with the `demo` profile, and only into a database that does not have it yet.

**Every account's password is `TalentPipe@2026`.**

All addresses use the reserved `.test` domain, so nothing can ever be emailed
to a real person — even with a Resend key configured.

## Loading it

```bash
cd backend
SPRING_PROFILES_ACTIVE=demo ./mvnw spring-boot:run      # with your usual DB_URL / JWT_SECRET
```

The log says `Demo data seeded: 3 companies, 22 vacancies, 24 applications`.
Afterwards run the backend normally (no profile); the data stays. Running with
`demo` again does nothing once the data is there.

Dates are spread over the past ten weeks, and every deadline is in the future
relative to the day you seed, so seed close to the presentation.

To start again, drop the database volume (`docker compose down -v` in `infra/`)
and seed again. That deletes **everything** in the local database, not only the
demo data — take a `pg_dump` first if anything else in it matters.

## Logins

### Lumora Technologies — software (Information Technology, Finance & Banking)

| Who | Role | Email | Good for showing |
| --- | --- | --- | --- |
| Dilini Wickramasinghe | Owner | `dilini.wickramasinghe@lumora.test` | Everything: team, roles and audit trail, company profile, settings |
| Kasun Perera | Recruiter | `kasun.perera@lumora.test` | All 11 vacancies, applicants, hiring teams, publishing |
| Shanaya Fernando | Recruiter | `shanaya.fernando@lumora.test` | Was promoted from Interviewer — visible in the role audit |
| Arjun Selvaraj | Interviewer | `arjun.selvaraj@lumora.test` | Sees only the 4 vacancies he is hiring manager of |
| Nimesha Jayasuriya | Interviewer | `nimesha.jayasuriya@lumora.test` | On two hiring teams, hiring manager of two more |
| Fathima Rizvi | Hiring Coordinator *(custom)* | `fathima.rizvi@lumora.test` | A company-defined role: team + interview scheduling, no vacancy editing |
| Tharindu Bandara | Junior Recruiter *(custom)* | `tharindu.bandara@lumora.test` | Can manage vacancies but sees only his own draft |
| Ishara Gunawardena | Interviewer | — | Invitation pending: shows on the team screen, cannot sign in yet |

### Serendib Health Partners — hospitals (Healthcare, Education)

Blind screening is switched on for this company.

| Who | Role | Email | Good for showing |
| --- | --- | --- | --- |
| Ruwan Abeysekera | Owner | `ruwan.abeysekera@serendibhealth.test` | A second, separate workspace |
| Priyanka Nadarajah | Recruiter | `priyanka.nadarajah@serendibhealth.test` | Clinical vacancies, rotating shifts, part-time roles |
| Hashan Weerasekara | Interviewer | `hashan.weerasekara@serendibhealth.test` | Hiring manager of the ICU vacancy |
| Malini Rodrigo | Clinical Panel *(custom)* | `malini.rodrigo@serendibhealth.test` | Sees applicants and conducts interviews only |

### Harbourline Logistics — freight (Logistics & Transport, Retail & E-commerce)

| Who | Role | Email | Good for showing |
| --- | --- | --- | --- |
| Imran Marikar | Owner | `imran.marikar@harbourline.test` | Third workspace |
| Chamari Dissanayake | Recruiter | `chamari.dissanayake@harbourline.test` | Six-day schedules, an hourly temporary role |
| Roshan de Silva | Interviewer | `roshan.desilva@harbourline.test` | Hiring manager of two vacancies |

### Candidates

| Who | Email | Applications |
| --- | --- | --- |
| Ashan Gunasekara | `ashan.gunasekara@mail.test` | Shortlisted at Lumora, rejected at Harbourline |
| Tharushi Perera | `tharushi.perera@mail.test` | Three, incl. a closed vacancy |
| Mohamed Aadhil | `mohamed.aadhil@mail.test` | Backend (in review), DevOps (shortlisted) |
| Kavindya Silva | `kavindya.silva@mail.test` | Frontend, Product Designer |
| Shalini Ramanathan | `shalini.ramanathan@mail.test` | Data Analyst, Logistics Coordinator |
| Nuwan Jayawardena | `nuwan.jayawardena@mail.test` | ICU nurse (shortlisted), training coordinator |
| Sachini Dias | `sachini.dias@mail.test` | Pharmacist, ICU nurse |
| Dinesh Kumar Rajendran | `dinesh.rajendran@mail.test` | Two at Harbourline |
| Hiruni Wijesinghe | `hiruni.wijesinghe@mail.test` | Fresh graduate: internship, QA, backend |
| Yohan Fernando | `yohan.fernando@mail.test` | One at each company |

### The workspace picker

`sanduni.karunaratne@talentconsult.test` is a Recruiter at Serendib Health, an
Interviewer at Harbourline and a candidate — all with the same password. Signing
in without a workspace answers `409 WORKSPACE_SELECTION_REQUIRED` listing the
three accounts; `POST /api/v1/auth/login/select` signs in to the chosen one.

> The current frontend does not have the picker step or permission-based menus
> yet (see [FRONTEND_CHANGES.md](FRONTEND_CHANGES.md)). In the UI, use the
> Owner, Recruiter, Interviewer and candidate logins; show the picker and the
> custom roles through the API (Postman), or after the frontend update.

## A ten-minute walkthrough

1. **Public board, signed out.** 15 live jobs from three companies. Filter by
   *Remote*, by *Contract*, by salary (monthly LKR 140,000–210,000), by industry —
   totals stay right because filtering is on the server.
2. **Candidate** — Ashan: his applications and their statuses.
3. **Owner** — Dilini: the team (including a pending invitation), the roles with
   two custom ones, the audit trail, the company profile with its industries,
   and the switches (AI screening defaults on).
4. **Recruiter** — Kasun: every vacancy in each state, a hiring team, the
   applicants for *Senior Backend Engineer*.
5. **Interviewer** — Arjun: only the four vacancies he works on; any other
   vacancy is "not found".
6. **Second company** — Ruwan at Serendib: a completely separate workspace.
