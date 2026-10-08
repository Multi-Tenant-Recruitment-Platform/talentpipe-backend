package com.talentpipe.job.service;

import com.talentpipe.tenant.dto.CompanySummaryResponse;
import com.talentpipe.tenant.service.TenantService;
import java.time.Clock;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Answers "what time is it?" and "what day is it <em>for this company</em>?"
 * for the job module.
 *
 * <p>The second question is the reason this class exists. An application
 * deadline is a calendar day, not an instant, and the day changes at a
 * different moment for each company: at 20:00 UTC it is already tomorrow in
 * Colombo. Judging a deadline against the server's date would close a Sri
 * Lankan company's vacancy five and a half hours late — or refuse to publish
 * one whose deadline is, to the recruiter, still today. So deadlines are
 * always compared with the date in the tenant's own timezone.</p>
 *
 * <p>All time comes from the injected {@link Clock}, so tests can pin it.</p>
 */
@Component
public class VacancyCalendar {

    private final Clock clock;
    private final TenantService tenantService;

    public VacancyCalendar(Clock clock, TenantService tenantService) {
        this.clock = clock;
        this.tenantService = tenantService;
    }

    /**
     * The current instant — what lifecycle timestamps are stamped with.
     *
     * <p>Cut to microseconds, which is all a PostgreSQL {@code TIMESTAMPTZ}
     * keeps. The JVM clock is finer than that, and PostgreSQL would round the
     * surplus away on write — so the {@code publishedAt} in the response to a
     * publish would differ, in its last digits, from the one every later read
     * returns. Stamping what will actually be stored keeps "the vacancy as now
     * stored" exactly true.</p>
     */
    public Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
    }

    /** Today's date for the given tenant; looks the tenant's timezone up. */
    public LocalDate today(UUID tenantId) {
        CompanySummaryResponse company = tenantService.findCompanySummaries(Set.of(tenantId)).get(tenantId);
        return today(company == null ? null : company.timezone());
    }

    /**
     * Today's date in the given timezone — for callers that already hold the
     * company's summary and should not fetch it again.
     *
     * @param timezone an IANA zone id such as {@code Asia/Colombo}; may be
     *                 {@code null} or unrecognized, in which case UTC is used
     */
    public LocalDate today(String timezone) {
        return LocalDate.now(clock.withZone(zoneOf(timezone)));
    }

    /**
     * Resolves a stored timezone string, falling back to UTC. The profile
     * field is free text, so an unset or unparseable value must degrade to a
     * sane default rather than fail a publish with a 500.
     */
    static ZoneId zoneOf(String timezone) {
        if (timezone == null || timezone.isBlank()) {
            return ZoneOffset.UTC;
        }
        try {
            return ZoneId.of(timezone.trim());
        } catch (DateTimeException ex) {
            return ZoneOffset.UTC;
        }
    }
}
