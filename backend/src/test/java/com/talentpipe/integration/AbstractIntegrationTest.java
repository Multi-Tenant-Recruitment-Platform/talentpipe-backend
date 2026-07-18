package com.talentpipe.integration;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
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
                "talentpipe.security.jwt.refresh-token-ttl=7d"
        })
@Testcontainers(disabledWithoutDocker = true)
public abstract class AbstractIntegrationTest {

    /**
     * pgvector/pgvector:pg15 = PostgreSQL 15 + pgvector, matching
     * infra/docker-compose.yml — V1__extensions.sql needs the extension
     * available at migration time.
     */
    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg15").asCompatibleSubstituteFor("postgres"));
}
