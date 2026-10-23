# Sprint Scope – Pipeline Template Creation

Oct 21, 2026 · @Anuja Gamage

This sprint delivers one thing: a company admin can create, view, edit and archive pipeline templates, with the frontend screens and the backend API behind them. Jobs, applications, AI scoring, interviews, blind mode and analytics come in later sprints. Items marked *to confirm* are my assumptions.

## 1. Scope

| In this sprint | Not in this sprint |
| --- | --- |
| Template list, create, edit and archive screens | Job creation and the snapshot copy |
| Stage builder (stages, order, interview rounds, benchmarks) | Applications and candidates |
| Default thresholds and ideal-matching setting | AI scoring and the screening rule engine |
| Backend API, validation and database tables for templates | Interviewer scores, blind mode, notifications, analytics |
| Company admin permission and tenant isolation | CV pool and headhunting |

The tables are built so the later job snapshot can copy a template without changing them.

## 2. What a template contains

- **Name and description** of the template.
- **Stages in order:** Post Application (first, fixed), Screening, one or more Interview rounds, Onboard (last, fixed).
- **Benchmark for each interview round,** set by the company.
- **Default thresholds:** auto threshold (default 80) and manual threshold (default 70). A job can edit them later.
- **Ideal-person matching:** available for jobs using this template or not.

Blind mode, roles per stage and the scorecard are set per job, so they are not part of the template.

## 3. User stories and acceptance criteria

| ID | Story | Acceptance criteria |
| --- | --- | --- |
| PT-1 | As a company admin, I create a pipeline template | Name is required and unique in the company. At least the fixed stages and one interview round are saved. Saved template appears in the list |
| PT-2 | As a company admin, I add and order stages | I can add an interview round, remove a round, and reorder the middle stages. Post Application stays first and Onboard stays last |
| PT-3 | As a company admin, I set a benchmark for each interview round | Benchmark is required for every interview round and must be a valid score |
| PT-4 | As a company admin, I set default thresholds | Auto threshold is greater than the manual threshold. Both are between 0 and 100. Defaults 80 and 70 are pre-filled |
| PT-5 | As a company admin, I choose whether ideal-person matching is available | A switch saved with the template |
| PT-6 | As a company admin, I view and search my templates | List shows name, number of stages, number of interview rounds and last updated. Archived templates are hidden by default |
| PT-7 | As a company admin, I edit a template | Changes are saved. Existing jobs are not affected because jobs keep their own copy |
| PT-8 | As a company admin, I archive a template | An archived template cannot be chosen for new jobs. It is not deleted, because existing jobs refer to it |
| PT-9 | As a job creator, I can see the list of active templates | Read-only access, ready for the job form in the next sprint |

## 4. Frontend

### 4.1 Screens

| Screen | Purpose |
| --- | --- |
| Template list | Table of templates with search, an archived filter and a Create button |
| Template editor (create and edit) | One form: details, stage builder, thresholds, ideal-matching switch |
| Archive confirmation | Dialog explaining that existing jobs are not affected |

### 4.2 Template editor behavior

- **Stage builder:** shows Post Application and Onboard as locked rows. Screening and interview rounds sit between them. Each interview round has a name and a benchmark field. The admin can add a round, remove a round and reorder with drag and drop or up and down buttons.
- **Thresholds:** two number fields pre-filled with 80 and 70. The form shows an error when the manual threshold is not lower than the auto threshold or a value is outside 0 to 100.
- **Validation:** errors show next to the field and the Save button stays disabled until the form is valid. The backend validates again.
- **States:** loading, empty list message, save success message, and server error message with a retry.
- **Access:** the Create, Edit and Archive actions show only for a company admin. Other roles see the list only.

## 5. Backend

### 5.1 API

| Method and path | Purpose | Who |
| --- | --- | --- |
| `POST /pipeline-templates` | Create a template with its stages | Company admin |
| `GET /pipeline-templates` | List templates (filters: search, include archived) | Company admin, job creator |
| `GET /pipeline-templates/{id}` | Get one template with stages | Company admin, job creator |
| `PUT /pipeline-templates/{id}` | Replace details, stages and thresholds | Company admin |
| `POST /pipeline-templates/{id}/archive` | Archive a template | Company admin |

The template and its stages are saved together in one database transaction, so a template never exists without its stages.

### 5.2 Validation rules

| Rule | Check |
| --- | --- |
| Name | Required, unique among the company's active templates |
| Stage order | Post Application first, Onboard last, positions are consecutive |
| Interview rounds | At least one interview round (*to confirm*) |
| Benchmark | Required on every interview round, between 0 and 100 (*scale to confirm*) |
| Thresholds | Manual threshold lower than auto threshold, both between 0 and 100 |
| Ownership | A user can only read or change templates of their own company |

### 5.3 Data model

| Table | Key fields |
| --- | --- |
| `pipeline_template` | id, tenant\_id, name, description, default\_auto\_threshold, default\_manual\_threshold, ideal\_matching\_enabled, status (active, archived), created\_by, created\_at, updated\_at |
| `pipeline_template_stage` | id, template\_id, position, type (post\_application, screening, interview, onboard), name, benchmark (interview only) |

- `pipeline_template_stage` is unique on (template\_id, position).
- Both tables carry the tenant, and PostgreSQL Row-Level Security limits every query to the current company.
- Archiving sets `status` to archived. Rows are never deleted, because the later job snapshot stores the source template id.

### 5.4 Permissions

The company admin can create, edit and archive. A job creator can only read active templates. Every endpoint checks the user's role and company.

## 6. Tasks

| Area | Tasks |
| --- | --- |
| Backend | Create the two tables and the Row-Level Security policies. Build the five endpoints. Add validation and the transaction. Add role checks |
| Frontend | Template list screen. Template editor with the stage builder. Form validation. Archive dialog. Connect the screens to the API. Hide actions by role |
| Tests | Unit tests for validation. API tests for each endpoint and for role and company isolation. Frontend tests for the form rules |

## 7. Definition of done

- An admin can create a template with stages, benchmarks, thresholds and the matching setting, and sees it in the list.
- Editing and archiving work, and archived templates are hidden by default.
- Invalid input is rejected on both the frontend and the backend.
- A user from another company cannot see or change the template.
- Tests pass and the API is documented.

## 8. Open items to confirm

1. Is Screening optional? Your notes say some jobs only have interviews.
    yes
2. Must a template have at least one interview round?
    yes at least one iterview
3. What is the score scale for interviewer scores and benchmarks? I assumed 0 to 100.
4. Do you need Duplicate template in this sprint? It saves time when companies have similar pipelines.
    yes
5. Can two templates have the same name if one is archived? I assumed yes.
    company admin should able delrte templete if needed