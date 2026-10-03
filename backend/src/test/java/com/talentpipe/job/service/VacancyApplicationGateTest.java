package com.talentpipe.job.service;

import static com.talentpipe.job.VacancyFixtures.NOW;
import static com.talentpipe.job.VacancyFixtures.TODAY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import com.talentpipe.common.exception.BusinessRuleException;
import com.talentpipe.common.exception.ResourceNotFoundException;
import com.talentpipe.job.VacancyFixtures;
import com.talentpipe.job.dto.VacancyApplicationTarget;
import com.talentpipe.job.entity.JobVacancy;
import com.talentpipe.job.repository.JobVacancyRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for {@link VacancyApplicationGate} — the rule that closed
 * vacancies stop accepting new applications (PB-020). The row lock it takes is
 * exercised in {@code JobVacancyFlowIntegrationTest}; here the decisions are.
 */
@ExtendWith(MockitoExtension.class)
class VacancyApplicationGateTest {

    private static final UUID TENANT = UUID.randomUUID();

    @Mock
    private JobVacancyRepository repository;

    @Mock
    private VacancyCalendar calendar;

    @InjectMocks
    private VacancyApplicationGate gate;

    @BeforeEach
    void setUp() {
        lenient().when(calendar.today(TENANT)).thenReturn(TODAY);
    }

    private JobVacancy stored(JobVacancy vacancy) {
        when(repository.findByIdForShare(vacancy.getId())).thenReturn(Optional.of(vacancy));
        return vacancy;
    }

    @Test
    void publishedVacancy_isOpen_andTheTargetNamesItsTenant() {
        JobVacancy vacancy = stored(VacancyFixtures.published(TENANT));

        VacancyApplicationTarget target = gate.requireOpen(vacancy.getId());

        assertThat(target.vacancyId()).isEqualTo(vacancy.getId());
        assertThat(target.tenantId()).isEqualTo(TENANT);
        assertThat(target.title()).isEqualTo("Senior Backend Engineer");
    }

    @Test
    void closedVacancy_refusesNewApplications() {
        JobVacancy vacancy = stored(VacancyFixtures.closed(TENANT));

        assertThatThrownBy(() -> gate.requireOpen(vacancy.getId()))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("This vacancy is closed and no longer accepts applications.");
    }

    @Test
    void archivedVacancy_refusesNewApplications() {
        JobVacancy vacancy = stored(VacancyFixtures.archived(TENANT));

        assertThatThrownBy(() -> gate.requireOpen(vacancy.getId()))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void draftVacancy_looksExactlyLikeOneThatDoesNotExist() {
        JobVacancy draft = stored(VacancyFixtures.draft(TENANT));
        UUID missing = UUID.randomUUID();
        when(repository.findByIdForShare(missing)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> gate.requireOpen(draft.getId()))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Job not found");
        assertThatThrownBy(() -> gate.requireOpen(missing))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Job not found");
    }

    @Test
    void publishedVacancy_onItsDeadlineDay_isStillOpen() {
        JobVacancy vacancy = new JobVacancy(TENANT, VacancyFixtures.complete().applicationDeadline(TODAY).build());
        vacancy.publish(NOW, TODAY);
        stored(VacancyFixtures.persisted(vacancy));

        assertThat(gate.requireOpen(vacancy.getId()).vacancyId()).isEqualTo(vacancy.getId());
    }

    @Test
    void publishedVacancy_pastItsDeadline_refusesAndSaysWhenItClosed() {
        JobVacancy vacancy = new JobVacancy(TENANT, VacancyFixtures.complete().applicationDeadline(TODAY).build());
        vacancy.publish(NOW, TODAY);
        stored(VacancyFixtures.persisted(vacancy));
        when(calendar.today(TENANT)).thenReturn(TODAY.plusDays(1));

        assertThatThrownBy(() -> gate.requireOpen(vacancy.getId()))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("Applications for this vacancy closed on 2026-10-03.");
    }
}
