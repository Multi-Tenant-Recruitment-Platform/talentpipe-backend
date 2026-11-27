package com.talentpipe.application.screening;

import com.talentpipe.application.service.ApplicationScreeningService;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Screens, on startup, every application that has never been scored —
 * for local development and demos, where applications predate screening.
 * Only with {@code talentpipe.ai.screening-backfill=true}.
 *
 * <p>Idempotent: an application that already has a score row is left alone,
 * and one on a vacancy that does not screen is only ever moved once, so
 * running it again changes nothing.</p>
 */
@Component
@ConditionalOnProperty(name = "talentpipe.ai.screening-backfill", havingValue = "true")
public class PendingScreeningBackfill implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(PendingScreeningBackfill.class);

    private final JdbcTemplate jdbc;
    private final ApplicationScreeningService screening;

    public PendingScreeningBackfill(JdbcTemplate jdbc, ApplicationScreeningService screening) {
        this.jdbc = jdbc;
        this.screening = screening;
    }

    @Override
    public void run(ApplicationArguments args) {
        // Already-decided applications are included: screening adds their score
        // but never overrides the recruiter's decision (see ApplicationScreeningService).
        List<UUID> waiting = jdbc.queryForList("""
                SELECT a.id FROM applications a
                WHERE a.review_status <> 'WITHDRAWN'
                  AND NOT EXISTS (SELECT 1 FROM application_scores s WHERE s.application_id = a.id)
                ORDER BY a.created_at, a.id
                """, UUID.class);
        if (waiting.isEmpty()) {
            log.info("Screening backfill: nothing waiting.");
            return;
        }
        Map<ApplicationScreeningService.Result, Integer> results =
                new EnumMap<>(ApplicationScreeningService.Result.class);
        for (UUID id : waiting) {
            try {
                results.merge(screening.screen(id), 1, Integer::sum);
            } catch (RuntimeException ex) {
                log.warn("Screening backfill: application {} not screened ({})", id, ex.getClass().getSimpleName());
            }
        }
        log.info("Screening backfill: {} application(s) processed: {}", waiting.size(), results);
    }
}
