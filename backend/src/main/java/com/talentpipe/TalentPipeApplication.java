package com.talentpipe;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * TalentPipe — Multi-Tenant Recruitment Intelligence Platform.
 *
 * <p>Architecture: modular monolith. Every business capability lives in its own
 * package under {@code com.talentpipe.<module>} (auth, tenant, job, candidate,
 * pipeline, ai, notification, analytics) plus the shared {@code common} kernel
 * and the {@code security} infrastructure package.</p>
 *
 * <p>Module ground rules (enforced by convention and code review):</p>
 * <ul>
 *   <li>No cross-module entity imports — modules collaborate through services
 *       and DTOs only.</li>
 *   <li>Entities never cross the controller boundary; controllers speak DTOs.</li>
 *   <li>Tenant identity is derived from context ({@code TenantContext}), never
 *       from request bodies or path parameters.</li>
 * </ul>
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class TalentPipeApplication {

    public static void main(String[] args) {
        SpringApplication.run(TalentPipeApplication.class, args);
    }
}
