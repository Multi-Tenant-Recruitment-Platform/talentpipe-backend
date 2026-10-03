package com.talentpipe.job.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.talentpipe.tenant.dto.CompanySummaryResponse;
import com.talentpipe.tenant.service.TenantService;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link VacancyCalendar}. The clock is pinned to an instant
 * that falls on two different calendar days depending on where you are, which
 * is exactly the situation the class exists to get right.
 */
class VacancyCalendarTest {

    /** 20:00 UTC on 3 October — already 01:30 on 4 October in Colombo (UTC+05:30). */
    private static final Instant LATE_EVENING_UTC = Instant.parse("2026-10-03T20:00:00Z");

    private final TenantService tenantService = mock(TenantService.class);
    private final VacancyCalendar calendar =
            new VacancyCalendar(Clock.fixed(LATE_EVENING_UTC, ZoneOffset.UTC), tenantService);

    @Test
    void now_isTheClocksInstant() {
        assertThat(calendar.now()).isEqualTo(LATE_EVENING_UTC);
    }

    @Test
    void now_isCutToMicroseconds_theFinestPrecisionPostgresStores() {
        Instant finerThanTheDatabase = Instant.parse("2026-10-03T12:01:09.069569600Z");
        VacancyCalendar fineClock =
                new VacancyCalendar(Clock.fixed(finerThanTheDatabase, ZoneOffset.UTC), tenantService);

        assertThat(fineClock.now()).isEqualTo(Instant.parse("2026-10-03T12:01:09.069569Z"));
    }

    @Test
    void today_isTheDateInTheGivenTimezone_notTheServers() {
        assertThat(calendar.today("Asia/Colombo")).isEqualTo(LocalDate.of(2026, 10, 4));
        assertThat(calendar.today("UTC")).isEqualTo(LocalDate.of(2026, 10, 3));
        assertThat(calendar.today("America/Los_Angeles")).isEqualTo(LocalDate.of(2026, 10, 3));
    }

    @Test
    void today_withNoTimezone_fallsBackToUtc() {
        assertThat(calendar.today((String) null)).isEqualTo(LocalDate.of(2026, 10, 3));
        assertThat(calendar.today("  ")).isEqualTo(LocalDate.of(2026, 10, 3));
    }

    @Test
    void today_withAnUnrecognizedTimezone_fallsBackToUtc_ratherThanFailing() {
        assertThat(calendar.today("Not/AZone")).isEqualTo(LocalDate.of(2026, 10, 3));
        assertThat(calendar.today("(GMT+05:30) Colombo")).isEqualTo(LocalDate.of(2026, 10, 3));
    }

    @Test
    void today_forATenant_usesThatTenantsConfiguredTimezone() {
        UUID tenantId = UUID.randomUUID();
        when(tenantService.findCompanySummaries(Set.of(tenantId))).thenReturn(Map.of(tenantId,
                new CompanySummaryResponse(tenantId, "Acme", "acme", null, "Asia/Colombo")));

        assertThat(calendar.today(tenantId)).isEqualTo(LocalDate.of(2026, 10, 4));
    }

    @Test
    void today_forATenantThatCannotBeResolved_fallsBackToUtc() {
        UUID tenantId = UUID.randomUUID();
        when(tenantService.findCompanySummaries(Set.of(tenantId))).thenReturn(Map.of());

        assertThat(calendar.today(tenantId)).isEqualTo(LocalDate.of(2026, 10, 3));
    }

    @Test
    void zoneOf_trimsSurroundingWhitespace() {
        assertThat(VacancyCalendar.zoneOf(" Asia/Colombo ").getId()).isEqualTo("Asia/Colombo");
    }
}
