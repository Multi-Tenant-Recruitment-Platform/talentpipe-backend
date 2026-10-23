# Talent Pipe – Workflow and Software Design Specification

Oct 21, 2026 · @Anuja Gamage

Talent Pipe moves each job application through a company-defined pipeline, and an AI score decides who advances. This document explains the workflow, how the frontend and backend work, and the software design specification (SDS). Items that are not in your notes are marked *to confirm*.

## 1. Scope and assumptions

- **In scope:** pipeline templates, jobs, applications, AI screening, interview rounds with benchmarks, blind mode, scorecard (ideal-person matching), onboarding, notifications and analytics.
- **Out of scope (future sprint):** CV pool matching and headhunting. The data model leaves room for it.
- **Known stack:** PostgreSQL with Row-Level Security (multi-tenant) and pgvector (embeddings for AI matching).
- **To confirm:** frontend framework, backend framework, AI model provider, queue or job runner.

## 2. End-to-end workflow

### 2.1 Setup by the company admin

1. The admin creates one or more **pipeline templates**.
2. Each template defines the stages and their order (Screening, interview rounds, Onboard), the benchmark of each interview round, and the default thresholds.
3. The admin chooses whether ideal-person matching is available for the company.

### 2.2 Job creation

1. The job creator opens the job form and **selects the relevant template**. Stages cannot be added or removed.
2. The system **copies the template into the job** (snapshot). Later template edits do not change this job.
3. The job creator fills in the title, description and requirements. The AI score is generated from these.
4. The job creator keeps the default thresholds or edits them for this job.
5. The job creator assigns people and roles per stage (recruiter, interviewer) and sets blind mode on or off.
6. HR optionally adds the scorecard: the skills and experience of the perfect candidate.
7. The job is published.

### 2.3 Application and screening

1. A candidate applies with a CV. The application starts in **Post Application**.
2. The system generates the **AI score** by comparing the CV with the job requirements and description.
3. The screening rule runs on the score:

| AI score | Result |
| --- | --- |
| 80 or above (default) | Moves to the next stage automatically |
| 70 or above, below 80 (default) | Waits in manual review. A person with the assigned role moves it on or rejects it |
| Below 70 (default) | Rejected, and the candidate receives a rejection notification |

4. If ideal-person matching is used as a rule, the candidate must also pass the scorecard rule. If it is used as an extra score, reviewers only see it.
5. Shortlisting is done inside Screening, based on these results.

### 2.4 Interview rounds

1. A pipeline can have several interview rounds. Each round has a **responsible person** and a **benchmark** set by the company.
2. The interviewer submits a score for the round.
3. If the interviewer score is **below the benchmark**, the candidate is rejected and receives a rejection notification.
4. Otherwise the candidate continues to the next round or to Onboard (*to confirm: automatic or manual move*).
5. The AI score and the interviewer score stay **separate** and are never merged.

### 2.5 Onboarding

1. The candidate reaches **Onboard**, which marks the candidate as **hired**.
2. If blind mode was on, the hidden identity (name, photo, university) is **revealed** at this stage.

### 2.6 Application statuses

| Status | Meaning |
| --- | --- |
| active | The candidate is in a pipeline stage |
| manual\_review | AI score is in the manual band and waits for a decision |
| rejected | Rejected by the AI threshold, an interview benchmark or a manual decision |
| hired | Reached Onboard |
| scoring\_failed | The AI score could not be generated. Goes to manual review |

## 3. Actors and permissions

| Actor | Can do |
| --- | --- |
| Company admin | Create and edit pipeline templates, manage company users and roles |
| Job creator | Create jobs from a template, edit thresholds, assign roles, set blind mode |
| HR | Add the scorecard for a job |
| Recruiter | View applications, make manual moves in manual review, reject candidates |
| Interviewer | See assigned candidates, submit interview scores (blind view when blind mode is on) |
| Candidate | Apply, upload a CV, receive status and rejection notifications |
| System (AI) | Generate the AI score, run the screening rule, send notifications |

Only a person with the assigned role for that job and stage can make a manual move.

## 4. Frontend: how it works

### 4.1 Screens

| Screen | Used by | Purpose |
| --- | --- | --- |
| Template list and editor | Company admin | Create templates: stages, round benchmarks, default thresholds |
| Job form | Job creator | Pick a template, fill job fields, thresholds, roles, blind mode, scorecard |
| Pipeline board | Recruiter, HR | One column per stage of the job, cards for candidates, manual review lane |
| Candidate review panel | Recruiter, interviewer | CV, AI score, scorecard gaps, history, actions |
| Interview scoring form | Interviewer | Submit the round score and notes |
| Application status page | Candidate | See the current status and notifications |
| Analytics dashboard | Admin, HR | Charts per job and per company |

### 4.2 Behavior

- **Job form:** choosing a template loads its stages as read-only. Threshold fields are pre-filled with the template defaults and can be edited. The form checks that the manual threshold is lower than the auto threshold and that both are between 0 and 100.
- **Pipeline board:** columns come from the job's snapshot stages. Each card shows the AI score. Candidates scoring in the manual band appear in a manual review lane. Move and reject buttons show only to users with the assigned role.
- **Blind mode:** the panel shows a candidate code instead of name, photo and university. The frontend only displays what the API returns, because masking is done in the backend.
- **AI score loading:** a new application shows a scoring state until the score arrives (polling or push, *to confirm*).
- **Feedback:** every move, rejection and score submit shows a success or error message, and the board refreshes.

## 5. Backend: how it works

### 5.1 Modules

| Module | Responsibility |
| --- | --- |
| Auth and tenancy | Login, roles, tenant context for every request |
| Template service | Create and edit templates and their stages |
| Job service | Create jobs, copy the template into the job (snapshot), roles, blind mode, scorecard |
| Application service | Receive applications, hold status and current stage |
| Scoring service | Call the AI model, store AI and scorecard scores |
| Pipeline engine | Evaluate screening rules, validate and apply stage transitions, write history |
| Interview service | Collect interviewer scores, compare with the benchmark |
| Masking | Remove name, photo and university from responses when blind mode is on |
| Notification service | Send rejection and status notifications |
| Analytics service | Calculate score comparison, recruitment time and stage conversion |

### 5.2 Screening flow

1. `POST /jobs/{id}/applications` creates the application and returns immediately.
2. The backend puts a **scoring task** in a queue.
3. A worker reads the CV text and the job description, calls the AI model and receives a score from 0 to 100.
4. The score is saved as an `ai` score for that application.
5. The pipeline engine reads the job's thresholds and applies the rule with greater than or equal comparison.
6. The engine moves the application, puts it in manual review or rejects it, and writes a history record.
7. For a rejection, the notification service sends the candidate a notification.

If scoring fails after retries, the application becomes `scoring_failed` and goes to manual review. The scoring task is safe to repeat, so a retry never creates two scores.

### 5.3 Stage transitions

- A transition is allowed only to the next stage in the job's snapshot, or to rejected.
- Every transition records who did it (system or a user), the action and the time.
- A manual move is accepted only from a user with the assigned role for that job.

### 5.4 API (draft)

| Method and path | Purpose |
| --- | --- |
| `POST /pipeline-templates` | Create a template with stages |
| `GET /pipeline-templates` | List company templates |
| `POST /jobs` | Create a job from a template id (copies the stages) |
| `PATCH /jobs/{id}/thresholds` | Edit thresholds for one job |
| `PUT /jobs/{id}/members` | Assign roles per stage |
| `PUT /jobs/{id}/scorecard` | Save the scorecard |
| `POST /jobs/{id}/applications` | Apply to a job |
| `GET /jobs/{id}/pipeline` | Board data: stages and candidates (masked when blind) |
| `POST /applications/{id}/move` | Manual move to the next stage |
| `POST /applications/{id}/reject` | Manual rejection |
| `POST /applications/{id}/interview-scores` | Submit an interviewer score |
| `GET /analytics/jobs/{id}` | Analytics for one job |
| `GET /analytics/company` | Company-wide analytics |

### 5.5 Multi-tenancy

Every tenant table has a `tenant_id`. PostgreSQL Row-Level Security policies compare it with the tenant set for the current request, so one company can never read another company's rows.

## 6. Software Design Specification (SDS)

### 6.1 Architecture

| Component | Role |
| --- | --- |
| Web client | The screens in section 4 |
| API server | Authentication, validation, modules in section 5 |
| Queue and worker | Runs AI scoring and notifications outside the request |
| PostgreSQL | Main data store with Row-Level Security |
| pgvector | Stores CV and job embeddings for AI matching (and for the future CV pool) |
| AI model service | Returns the AI score for a CV and a job (*provider to confirm*) |
| Notification channel | Sends rejection and status messages (*email or in-app, to confirm*) |

### 6.2 Data model

| Table | Key fields |
| --- | --- |
| `pipeline_template` | id, tenant\_id, name, default\_auto\_threshold, default\_manual\_threshold, ideal\_matching\_enabled |
| `pipeline_template_stage` | id, template\_id, position, type (screening, interview, onboard), name, benchmark (interview only) |
| `job` | id, tenant\_id, title, description, requirements, status, blind\_mode, auto\_threshold, manual\_threshold, ideal\_person\_mode (off, extra\_score, rule), source\_template\_id |
| `job_stage` | id, job\_id, position, type, name, benchmark. A copy of the template stages (snapshot) |
| `job_stage_assignee` | id, job\_stage\_id, user\_id, role (recruiter, interviewer) |
| `scorecard_criterion` | id, job\_id, skill, required\_experience, weight |
| `candidate_cv` | id, candidate\_id, file reference, parsed\_text, embedding (vector), pool\_consent (future) |
| `application` | id, tenant\_id, job\_id, candidate\_id, cv\_id, current\_stage\_id, status, applied\_at, decided\_at |
| `application_score` | id, application\_id, type (ai, ideal\_person, interviewer), stage\_id (interviewer only), score, scorer\_id (interviewer only), details (JSON), created\_at |
| `application_stage_history` | id, application\_id, from\_stage\_id, to\_stage\_id, action (auto\_advance, manual\_advance, reject), actor\_id (empty for system), reason, created\_at |
| `notification` | id, tenant\_id, recipient\_id, type, payload, status, created\_at |

Relationships:

- A tenant has many templates. A template has many stages.
- A job is created from one template and owns its own copy of the stages (`job_stage`), so template edits never reach it.
- A job has many applications. An application points to one candidate CV and one current job stage.
- An application has many scores (AI, ideal person, one per interviewer round) and many history records.

Constraints: `job_stage` is unique on (job\_id, position). `manual_threshold` must be lower than `auto_threshold`. Both are between 0 and 100.

### 6.3 Business rules

| ID | Rule |
| --- | --- |
| BR-1 | Auto-advance when AI score is greater than or equal to the auto threshold |
| BR-2 | Manual review when the score is greater than or equal to the manual threshold and below the auto threshold |
| BR-3 | Reject when the score is below the manual threshold, and notify the candidate |
| BR-4 | The default thresholds come from the template and can be edited per job |
| BR-5 | A job uses a copy of its template. Stages cannot be added or removed on a job |
| BR-6 | An interviewer score below the round benchmark rejects the candidate and sends a notification |
| BR-7 | AI score and interviewer score are stored and shown separately |
| BR-8 | Ideal-person matching is the company's choice: an extra reviewer score or a rule next to the AI score |
| BR-9 | With blind mode on, name, photo and university are hidden until Onboard |
| BR-10 | Only a user with the assigned role can make a manual move for that job |
| BR-11 | Reaching Onboard marks the application as hired |

### 6.4 Key design decisions

- **Snapshot of the template:** protects running jobs from template edits. Costs a little extra storage per job.
- **Scores in one table by type:** keeps AI, scorecard and interviewer scores separate and makes analytics a simple query.
- **Masking in the backend:** hiding data only in the screen would still leak it through the API.
- **AI scoring in a queue:** a slow model call never blocks the applicant, and failures can be retried.
- **History table:** every move is recorded, which supports audit and the stage conversion analytics.

### 6.5 Analytics

| Metric | Source |
| --- | --- |
| AI score vs interviewer score | `application_score` by type, per job and per company |
| Recruitment time | `application.applied_at` to the hired time in `application_stage_history` |
| Percentage moving between stages | Counts per stage from `application_stage_history` |

### 6.6 Non-functional requirements

- **Security:** role checks on every endpoint, Row-Level Security on tenant tables, masking for blind mode.
- **Privacy:** CV data is personal data. The future CV pool needs the candidate's consent before any company can see a CV.
- **Reliability:** AI scoring retries and falls back to manual review.
- **Auditability:** every stage change is stored with actor and time.
- **Performance:** the application request returns quickly. Scoring runs in the background.

### 6.7 Future work

- CV pool matching with the same AI and pgvector search, shown to companies only for matching CVs.
- Headhunting contact flow for candidates in the pool.

## 7. Open items to confirm

1. Which frontend and backend frameworks and which AI model provider will be used?
2. After passing an interview benchmark, does the candidate move on automatically, or does the responsible person move them?
3. Should a candidate in manual review be reminded or auto-rejected after some time, or wait without a limit?
4. Does the candidate see their stage in the pipeline, or only the final result and the rejection notification?
