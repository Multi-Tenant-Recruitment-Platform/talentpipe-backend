## What does this PR do?

<!-- 1–3 sentences. What changes and why? Link the task/story if there is one (e.g. PB-012). -->


## Type of change

- [ ] New feature
- [ ] Bug fix
- [ ] Refactor (no behavior change)
- [ ] Docs / config / CI
- [ ] Database migration (Flyway)

## Checklist

- [ ] `./mvnw test` passes locally (unit + integration if Docker is running)
- [ ] New/changed behavior is covered by tests
- [ ] No secrets, tokens or credentials committed
- [ ] Migrations are forward-only (no edits to already-applied `V*.sql` files)
- [ ] Architecture rules respected (no cross-module entity imports, DTOs at controllers, tenant from context only)
- [ ] README / docs updated if behavior or setup changed

## How to verify

<!-- Steps for the reviewer: commands to run, endpoints to call, expected results. -->
