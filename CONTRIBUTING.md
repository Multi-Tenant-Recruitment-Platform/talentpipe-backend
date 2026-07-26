# Contributing to TalentPipe Backend

## Branching model

We use a two-branch flow (GitFlow-lite):

```
master        ← production-ready, always releasable
development   ← integration branch, where features meet
feature/*     ← every task gets its own branch off development
```

- **Never commit directly to `master` or `development`** — both are protected.
- Branch off `development`: `git checkout -b feature/pb-012-job-crud development`
- Open a PR back into `development`. Releases are cut by PR'ing `development` → `master`.

## Pull request rules

| Target branch | Approvals required | Other gates |
|---|---|---|
| `master` | **2** | CI green, branch up to date, CODEOWNERS review |
| `development` | **1** | CI green, branch up to date |

- Fill in the PR template — reviewers reject empty descriptions.
- Keep PRs small and single-purpose. A PR should do one thing.
- Resolve review comments by pushing new commits; don't force-push over a reviewed branch.
- The PR author merges after approval (squash merge preferred for feature PRs).

## Commit messages

Conventional-commit style, matching the repo history:

```
feat(auth): add password history check
fix(team): return 404 for cross-tenant invitation resend
test(security): cover refresh token replay
docs: update API table in README
chore/ci/refactor: ...
```

## Before you push

```bash
cd backend
./mvnw test        # unit tests always; integration tests need Docker running
```

CI runs the exact same command on every PR — red CI means the PR cannot merge.

## Hard rules (architecture)

1. No cross-module entity/repository imports — modules talk via services and DTOs.
2. Entities never cross a controller boundary — DTOs in, DTOs out.
3. Tenant identity comes from `TenantContext` / JWT claims only — never from request bodies or path params.
4. Flyway migrations are forward-only — never edit an already-applied `V*.sql`.
5. No secrets in the repo, ever. `.env` is gitignored; keys come from environment variables.
6. 404 over 403 for cross-tenant resources; generic 401 for credential failures; always-200 for enumeration-prone endpoints (forgot-password, resend-verification).

See `docs/DECISIONS.md` for the reasoning behind each rule.
