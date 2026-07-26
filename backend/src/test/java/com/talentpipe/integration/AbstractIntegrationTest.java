package com.talentpipe.integration;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * Base for full-stack integration tests: real Spring context on a random
 * port, real PostgreSQL via Testcontainers (so Postgres-specific pieces —
 * uuid-ossp, pgvector, TIMESTAMPTZ, CHECK constraints, Flyway migrations —
 * are exercised, not simulated by H2).
 *
 * <p>{@code disabledWithoutDocker = true}: on machines without Docker these
 * tests are SKIPPED, not failed. CI and any Docker-equipped dev machine runs
 * them.</p>
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                // Deterministic test secret — never a real one.
                "talentpipe.security.jwt.secret=integration-test-secret-0123456789-0123456789",
                "talentpipe.security.jwt.access-token-ttl=15m",
                "talentpipe.security.jwt.refresh-token-ttl=7d",
                // All test requests share one IP (127.0.0.1); raise the login
                // rate limit so the suite is deterministic. Dedicated lockout /
                // rate-limit tests can still assert the behavior explicitly.
                "talentpipe.security.login-rate-limit.max-per-minute=10000"
        })
@Testcontainers(disabledWithoutDocker = true)
public abstract class AbstractIntegrationTest {

    /**
     * pgvector/pgvector:pg15 = PostgreSQL 15 + pgvector, matching
     * infra/docker-compose.yml — V1__extensions.sql needs the extension
     * available at migration time.
     *
     * <p><b>Singleton container:</b> started once per JVM in the static
     * initializer and left running for the whole suite (Ryuk stops it at JVM
     * exit). Do NOT put {@code @Container} on this field: JUnit would stop the
     * container after the FIRST test class while Spring's cached application
     * context — shared by every integration class — keeps pointing at its dead
     * port (Hikari "connection refused" + 30s timeouts for classes 2..N).
     * {@code @Testcontainers(disabledWithoutDocker = true)} stays: it is the
     * execution condition that skips these tests when Docker is absent, and
     * the static initializer only runs when the tests are actually enabled.
     */
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg15").asCompatibleSubstituteFor("postgres"));

    static {
        POSTGRES.start();
    }
}
