# ---------------------------------------------------------------------------
# TalentPipe backend image.
#
#   docker build -t talentpipe-backend backend/
#
# Self-contained: compiles from source, so it needs neither a JDK nor a jar
# built beforehand on the machine running the build. The JDK and Maven exist
# only in the first stage. The image that ships holds the JRE and the
# application, and runs as an unprivileged user.
# ---------------------------------------------------------------------------

# --- Build --------------------------------------------------------------------
# Runs on the build machine's own platform: bytecode is the same on every CPU,
# so building the ARM image on an x86 CI runner (or the reverse) needs no
# emulation for the slow part.
FROM --platform=$BUILDPLATFORM eclipse-temurin:17-jdk-jammy AS build

WORKDIR /build

# Dependencies first, in their own layer, so a change to the sources reuses
# them. The cache mount keeps Maven's local repository between builds without
# putting it in any layer.
COPY .mvn/ .mvn/
COPY --chmod=0755 mvnw ./
COPY pom.xml ./
RUN --mount=type=cache,target=/root/.m2 ./mvnw -B -q dependency:go-offline

COPY src/ src/
# Tests run in CI before an image is built (see .github/workflows), not here.
RUN --mount=type=cache,target=/root/.m2 ./mvnw -B -q package -DskipTests \
 && cp target/talentpipe-backend-*.jar application.jar \
 && java -Djarmode=tools -jar application.jar extract --layers --destination extracted

# --- Runtime ------------------------------------------------------------------
FROM eclipse-temurin:17-jre-jammy

# A system user with a fixed id, no home and no login shell. The application
# files below stay owned by root, so the process cannot change its own code.
# /app/uploads is the one writable directory, for STORAGE_PROVIDER=local.
RUN groupadd --system --gid 10001 talentpipe \
 && useradd --system --uid 10001 --gid talentpipe --no-create-home \
      --home-dir /nonexistent --shell /usr/sbin/nologin talentpipe \
 && mkdir -p /app/uploads \
 && chown talentpipe:talentpipe /app/uploads

WORKDIR /app

# One layer per rate of change: libraries change rarely, the application on
# every commit, so a new release ships only its last layer.
COPY --from=build /build/extracted/dependencies/ ./
COPY --from=build /build/extracted/spring-boot-loader/ ./
COPY --from=build /build/extracted/snapshot-dependencies/ ./
COPY --from=build /build/extracted/application/ ./

USER 10001:10001

EXPOSE 8080

# Liveness only: whether the process should be restarted. It checks nothing
# outside the JVM, so a database outage never turns into a restart loop.
# (ECS ignores this instruction; its task definition has the same check.)
HEALTHCHECK --interval=15s --timeout=5s --start-period=90s --retries=3 \
  CMD ["sh", "-c", "curl -fsS \"http://127.0.0.1:${SERVER_PORT:-8080}/actuator/health/liveness\" > /dev/null || exit 1"]

# The heap follows the container's memory limit. On OutOfMemoryError the JVM
# exits and the orchestrator starts a clean one, instead of limping on. Extra
# flags go in JAVA_TOOL_OPTIONS, which the JVM reads by itself.
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-XX:+ExitOnOutOfMemoryError", "-jar", "application.jar"]
