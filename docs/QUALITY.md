# Code Quality — SonarQube / SonarCloud

Both repositories run static analysis (bugs, code smells, duplication, security
hotspots) plus test-coverage reporting through Sonar. Two ways to run it:

| | SonarCloud (recommended) | Local SonarQube |
|---|---|---|
| What | SaaS, integrated with GitHub PRs | Self-hosted via Docker |
| Cost | Free for public repos | Free (Community Edition) |
| Setup | ~10 min, one-time | already wired into `infra/docker-compose.yml` |

The CI `sonar` job is **inactive until `SONAR_TOKEN` exists** — nothing fails
before setup is complete.

## Option A — SonarCloud (CI, PR decoration)

1. Go to [sonarcloud.io](https://sonarcloud.io) → **Log in with GitHub**.
2. **+ → Analyze new project** → select the repository. SonarCloud shows the
   **organization key** and **project key** — copy both.
3. Create a token: **My Account → Security → Generate Token**.
4. In the GitHub repo → **Settings → Secrets and variables → Actions**:
   - Secret `SONAR_TOKEN` = the token from step 3
   - Variable `SONAR_ORGANIZATION` = org key
   - Variable `SONAR_PROJECT_KEY` = project key
   - (Variable `SONAR_HOST_URL` — only for self-hosted; omit for SonarCloud)
5. Open any PR — the `sonar` job runs and decorates the PR with issues,
   coverage and the quality-gate result.

Do this once per repository (backend and frontend are separate SonarCloud projects).

## Option B — Local SonarQube (self-hosted)

```bash
cd infra
docker compose --profile sonar up -d sonarqube
```

1. Open `http://localhost:9000` (first start takes ~1 min). Log in with
   `admin` / `admin` and set a new password.
2. **My Account → Security → Generate Token** (type: User Token).
3. Analyze the backend:

   ```bash
   cd backend
   # Windows:  .\mvnw.cmd ...  (same arguments)
   ./mvnw verify org.sonarsource.scanner.maven:sonar-maven-plugin:sonar `
     "-Dsonar.host.url=http://localhost:9000" `
     "-Dsonar.token=<your-token>" `
     "-Dsonar.projectKey=talentpipe-backend"
   ```

   JaCoCo coverage (written by `mvnw test` to `target/site/jacoco/jacoco.xml`)
   is picked up automatically.

4. Analyze the frontend — any SonarScanner works, e.g. Docker:

   ```bash
   docker run --rm -v ${PWD}:/usr/src `
     -e SONAR_HOST_URL=http://host.docker.internal:9000 `
     -e SONAR_TOKEN=<your-token> `
     sonarsource/sonar-scanner-cli
   ```

   (The repo root contains `sonar-project.properties`; the project key is
   `talentpipe-frontend`.)

## Quality gate

The default **Sonar Way** gate applies: no new blocker/critical issues, coverage
on new code ≥ 80%, duplication on new code ≤ 3%. Tune the thresholds in the
Sonar UI if the team agrees on different targets — record the decision in
`docs/DECISIONS.md`.
