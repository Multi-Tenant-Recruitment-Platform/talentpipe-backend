package com.talentpipe.job.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.talentpipe.auth.service.UserDirectoryService;
import com.talentpipe.common.exception.BusinessRuleException;
import com.talentpipe.job.VacancyFixtures;
import com.talentpipe.job.entity.PayPeriod;
import com.talentpipe.job.entity.VacancyContent;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for {@link VacancyContentRules} — the cross-field rules that hold
 * in every state, drafts included.
 */
@ExtendWith(MockitoExtension.class)
class VacancyContentRulesTest {

    private static final UUID TENANT = UUID.randomUUID();

    @Mock
    private UserDirectoryService userDirectory;

    @InjectMocks
    private VacancyContentRules rules;

    // ----------------------------------------------------------------- salary

    @Test
    void noSalary_needsNeitherCurrencyNorPayPeriod() {
        VacancyContent content = VacancyFixtures.complete().salary(null, null).build();

        assertThatCode(() -> rules.check(TENANT, content, null)).doesNotThrowAnyException();
    }

    @Test
    void fullSalaryRange_withCurrencyAndPeriod_passes() {
        VacancyContent content = VacancyFixtures.complete()
                .salary("450000", "650000").currency("LKR").payPeriod(PayPeriod.MONTHLY).build();

        assertThatCode(() -> rules.check(TENANT, content, null)).doesNotThrowAnyException();
    }

    @Test
    void equalMinimumAndMaximum_isAValidFixedSalary() {
        VacancyContent content = VacancyFixtures.complete()
                .salary("500000", "500000").currency("LKR").payPeriod(PayPeriod.MONTHLY).build();

        assertThatCode(() -> rules.check(TENANT, content, null)).doesNotThrowAnyException();
    }

    @Test
    void maximumBelowMinimum_isRefused() {
        VacancyContent content = VacancyFixtures.complete()
                .salary("650000", "450000").currency("LKR").payPeriod(PayPeriod.MONTHLY).build();

        assertThatThrownBy(() -> rules.check(TENANT, content, null))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("The maximum salary must be at least the minimum.");
    }

    @Test
    void salaryWithoutCurrency_isRefused_evenWithOnlyOneEndOfTheRange() {
        VacancyContent content = VacancyFixtures.complete()
                .salary("450000", null).payPeriod(PayPeriod.MONTHLY).build();

        assertThatThrownBy(() -> rules.check(TENANT, content, null))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("Choose a currency for the salary range you entered.");
    }

    @Test
    void salaryWithoutPayPeriod_isRefused() {
        VacancyContent content = VacancyFixtures.complete().salary(null, "650000").currency("LKR").build();

        assertThatThrownBy(() -> rules.check(TENANT, content, null))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("Say whether the salary range is hourly, monthly or yearly.");
    }

    // --------------------------------------------------------------- pipeline

    @Test
    void noPipeline_isFine() {
        VacancyContent content = VacancyFixtures.complete().recruitmentPipelineId(null).build();

        assertThatCode(() -> rules.check(TENANT, content, null)).doesNotThrowAnyException();
    }

    @Test
    void unknownPipeline_isRefused() {
        VacancyContent content = VacancyFixtures.complete().recruitmentPipelineId("MADE_UP").build();

        assertThatThrownBy(() -> rules.check(TENANT, content, null))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("Choose one of the available recruitment pipelines.");
    }

    // -------------------------------------------------------------- assignees

    @Test
    void activeMembers_canBeAssigned() {
        UUID recruiter = UUID.randomUUID();
        UUID manager = UUID.randomUUID();
        when(userDirectory.isActiveMember(TENANT, recruiter)).thenReturn(true);
        when(userDirectory.isActiveMember(TENANT, manager)).thenReturn(true);
        VacancyContent content = VacancyFixtures.complete()
                .assignedRecruiterId(recruiter).hiringManagerId(manager).build();

        assertThatCode(() -> rules.check(TENANT, content, null)).doesNotThrowAnyException();
    }

    @Test
    void recruiterWhoIsNotAnActiveMember_isRefused() {
        UUID outsider = UUID.randomUUID();
        when(userDirectory.isActiveMember(TENANT, outsider)).thenReturn(false);
        VacancyContent content = VacancyFixtures.complete().assignedRecruiterId(outsider).build();

        assertThatThrownBy(() -> rules.check(TENANT, content, null))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("The assigned recruiter must be an active member of your workspace.");
    }

    @Test
    void hiringManagerWhoIsNotAnActiveMember_isRefused() {
        UUID outsider = UUID.randomUUID();
        when(userDirectory.isActiveMember(TENANT, outsider)).thenReturn(false);
        VacancyContent content = VacancyFixtures.complete().hiringManagerId(outsider).build();

        assertThatThrownBy(() -> rules.check(TENANT, content, null))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("The hiring manager must be an active member of your workspace.");
    }

    @Test
    void anUnchangedAssignment_isNotReChecked_soADepartedRecruiterDoesNotBlockUnrelatedEdits() {
        UUID departed = UUID.randomUUID();
        VacancyContent stored = VacancyFixtures.complete().assignedRecruiterId(departed).build();
        VacancyContent edited = VacancyFixtures.complete()
                .assignedRecruiterId(departed).title("Corrected title").build();

        assertThatCode(() -> rules.check(TENANT, edited, stored)).doesNotThrowAnyException();

        verify(userDirectory, never()).isActiveMember(any(), any());
    }

    @Test
    void aChangedAssignment_isChecked() {
        UUID previous = UUID.randomUUID();
        UUID replacement = UUID.randomUUID();
        when(userDirectory.isActiveMember(TENANT, replacement)).thenReturn(false);
        VacancyContent stored = VacancyFixtures.complete().assignedRecruiterId(previous).build();
        VacancyContent edited = VacancyFixtures.complete().assignedRecruiterId(replacement).build();

        assertThatThrownBy(() -> rules.check(TENANT, edited, stored))
                .isInstanceOf(BusinessRuleException.class);
    }
}
