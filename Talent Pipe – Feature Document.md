# Talent Pipe – Feature Document

Recruitment SaaS platform · Feature specification (draft v1)

## 1. Overview

Talent Pipe is the hiring workflow of the platform. A candidate's application moves through a pipeline of stages (Post Application → Screening → Interview → Onboard). The system uses an AI score to shortlist candidates, supports blind review to reduce bias, and lets recruiters find candidates from a consent-based CV pool.

## 2. Pipeline templates

- A company can have **multiple pipeline templates**. Some jobs only need interviews; others need shortlisting plus two or more interview rounds.
- The **company admin** creates templates with common features that are not affected by any single job.
- When creating a job, the job creator **selects the relevant template** and fills in the fields unique to that job. The job crea1or cannot add or remove stages.
- **Snapshot rule:** when a job is created, it receives its own copy of the selected template. Later edits to the template do not affect existing jobs.

### Set in the template (admin)

| Item | Notes |
| --- | --- |
| Stages and their order | Screening, interview rounds, Onboard |
| Number of interview rounds | Fixed per template |
| Benchmark for each interview round | Set by the company |
| Default screening threshold | Used for auto-move and rejection |
| Ideal-person matching availability | Optional per company |
| Rejection notification behavior | Candidates are notified on rejection |

### Set per job (job creator)

| Item | Notes |
| --- | --- |
| Job title, description, requirements | The AI score is generated from these |
| Threshold override | Edit the template default if needed |
| Responsible people and roles per stage | For example recruiter, interviewer |
| Blind mode | On or off per job |
| Scorecard (ideal candidate profile) | Used by ideal-person matching |

## 3. Pipeline stages

1. **Post Application** – the candidate applies to a job.
2. **Screening** – the AI score is generated and shortlisting is done here.
3. **Interview** – one or more rounds. Each round has a responsible person and a benchmark.
4. **Onboard** – marks the candidate as **hired**. Identity is revealed here when blind mode is on.

## 4. Screening and AI score

- The **AI score is the primary screening rule**. An AI model compares the job requirements and job description with each applicant's CV and generates one score per applicant per job.
- The AI match analyzes the meaning of the CV content. It does not rely on keyword matching.
- The job creator decides the rule that checks whether a candidate moves to the next stage.

Default rule (thresholds are configurable per job):

| AI score | Result |
| --- | --- |
| 80 or above | Moves to the next stage automatically |
| 70 or above, but below 80 | Needs a manual move by a person with the assigned role |
| Below 70 | Rejected, and the candidate receives a rejection notification |

- The default threshold lives in the pipeline template. A job can edit it when needed. Thresholds are compared with greater than or equal (≥), so a score of exactly 80 moves automatically and exactly 70 goes to manual review.
- **Ideal-person matching** (scorecard) is optional. It is the company's choice: it can be used as an additional rule together with the AI matching score, or only as an extra score for reviewers.

## 5. Scorecard (ideal-person matching)

- HR adds the skills and experience of the perfect candidate to the job.
- The system compares that profile with each CV and identifies gaps.
- It is optional and separate from the AI score. The company chooses whether it works as a rule alongside the AI matching score or only as an extra score for reviewers.

## 6. Interviews

- A pipeline can have **multiple interview rounds**.
- Each round has its own **responsible person**.
- Each round has a **benchmark set by the company** (in the template).
- If an interviewer's score is **below the benchmark, the candidate is rejected and receives a rejection notification**.
- The AI score and the interviewer score are **two separate scores** and are never merged.

## 7. Blind mode

- Reduces bias by hiding the **name, photo and university** from interviewers.
- Interviewers see only the relevant academic and professional details.
- Blind mode is **set per job**.
- The hidden identity is **revealed at the Onboarding stage**.

## 8. Roles

- Specific roles are assigned per job, such as **recruiter** and **interviewer**.
- Only the person with the assigned role can make manual stage moves for that job.

## 9. CV pool and headhunting

- Candidates can upload their CV to the **CV pool** **with their consent**.
- After a recruiter posts a job, they can find relevant matches in the pool and contact them.
- A company can see **only the CVs that match a job**. It cannot read all the CVs in the pool. For a matching candidate, the company sees the full CV.
- Matching uses the **same AI as the AI score**. This is planned for a **future sprint** and is out of scope for the first release.

## 10. Analytics

Available **per job and per company**:

- AI score vs interviewer score
- Recruitment time
- Percentage of candidates who progress between each pipeline stage

## 11. Out of scope for now

- CV pool matching and headhunting search (future sprint)

## 12. Decisions from the review

1. **Boundary values:** thresholds use greater than or equal, so 80 moves automatically and 70 goes to manual review.
2. **CV pool visibility:** the company sees the full CV of a matching candidate.
3. **Benchmark rejection notification:** yes, a candidate rejected below an interview benchmark also receives a rejection notification.
4. **Ideal-person matching:** it is the company's choice, either a rule alongside the AI matching score or an extra score for reviewers.
