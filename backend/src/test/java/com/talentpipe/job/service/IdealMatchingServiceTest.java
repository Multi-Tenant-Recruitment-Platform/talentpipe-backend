package com.talentpipe.job.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.talentpipe.common.exception.BusinessRuleException;
import com.talentpipe.job.VacancyFixtures;
import com.talentpipe.job.dto.IdealMatchingRequest;
import com.talentpipe.job.dto.IdealMatchingResponse;
import com.talentpipe.job.dto.MatchingCriterionRequest;
import com.talentpipe.job.dto.ScreeningProfileResponse;
import com.talentpipe.job.entity.JobScreeningCriteria;
import com.talentpipe.job.entity.JobVacancy;
import com.talentpipe.job.entity.MatchingCategory;
import com.talentpipe.job.entity.MatchingRequirement;
import com.talentpipe.job.repository.JobScreeningCriteriaRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for a vacancy's ideal candidate: what may be stored in any state,
 * what publishing needs, and what screening is handed.
 */
@ExtendWith(MockitoExtension.class)
class IdealMatchingServiceTest {

    private static final UUID TENANT = UUID.randomUUID();
    private static final VacancyViewer VIEWER = new VacancyViewer(TENANT, UUID.randomUUID(), true);

    @Mock
    private VacancyAccess access;
    @Mock
    private JobScreeningCriteriaRepository criteriaRepository;
    @Captor
    private ArgumentCaptor<List<JobScreeningCriteria>> saved;

    private IdealMatchingService service;
    private JobVacancy vacancy;

    @BeforeEach
    void setUp() {
        service = new IdealMatchingService(access, criteriaRepository);
        vacancy = VacancyFixtures.draftOnPipeline(TENANT);
        vacancy.offerIdealMatching(true);
    }

    // ---------------------------------------------------------------- prepare

    @Test
    void prepare_putsTheSwitchAndListsOnTheVacancy_andReturnsTheCriteriaInOrder() {
        List<IdealMatchingService.Wanted> criteria = service.prepare(vacancy, profile(true,
                skill("Java", 60, MatchingRequirement.MUST_HAVE),
                skill("Kafka", 40, MatchingRequirement.PREFERRED)));

        assertThat(vacancy.isIdealMatchingEnabled()).isTrue();
        assertThat(vacancy.getMatchingDegreeTypes()).containsExactly("Computer Science");
        assertThat(vacancy.getMatchingNiceToHaveCertifications()).containsExactly("AWS Solutions Architect");
        assertThat(criteria).extracting(IdealMatchingService.Wanted::name).containsExactly("Java", "Kafka");
        assertThat(criteria).extracting(IdealMatchingService.Wanted::requirement)
                .containsExactly(MatchingRequirement.MUST_HAVE, MatchingRequirement.PREFERRED);
    }

    @Test
    void prepare_refusesMatchingWhereTheTemplateDoesNotOfferIt() {
        JobVacancy plain = VacancyFixtures.draftOnPipeline(TENANT);

        assertThatThrownBy(() -> service.prepare(plain, profile(true, skill("Java", 100, MatchingRequirement.MUST_HAVE))))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("does not offer ideal candidate matching");
    }

    @Test
    void prepare_refusesTheSameSkillTwice_ignoringCase() {
        assertThatThrownBy(() -> service.prepare(vacancy, profile(true,
                skill("Git", 50, MatchingRequirement.MUST_HAVE),
                skill("git", 50, MatchingRequirement.PREFERRED))))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("git is listed twice. Weigh each skill once.");
    }

    @Test
    void prepare_refusesASkillWithoutANameOrWithoutMustHaveOrPreferred() {
        assertThatThrownBy(() -> service.prepare(vacancy, profile(true, skill(" ", 50, MatchingRequirement.MUST_HAVE))))
                .hasMessage(IdealMatchingService.NEEDS_A_NAME);
        assertThatThrownBy(() -> service.prepare(vacancy, profile(true, skill("Java", 50, null))))
                .hasMessage("Say whether Java is a must-have or preferred.");
    }

    @Test
    void prepare_refusesAWeightOutsideOneToAHundred_forCallersThatSkipValidation() {
        assertThatThrownBy(() -> service.prepare(vacancy, profile(true, skill("Java", 150, MatchingRequirement.MUST_HAVE))))
                .hasMessage("Give Java a weight between 1% and 100%.");
    }

    @Test
    void prepare_dropsDegreeTypesWhenTheEducationIsNotADegree() {
        JobVacancy noDegree = VacancyFixtures.persisted(new JobVacancy(TENANT,
                VacancyFixtures.complete().education("Secondary education (O/L)").build()));
        noDegree.offerIdealMatching(true);

        service.prepare(noDegree, profile(true, skill("Java", 100, MatchingRequirement.MUST_HAVE)));

        assertThat(noDegree.getMatchingDegreeTypes()).isEmpty();
    }

    @Test
    void aDraft_mayBeSavedWithAnyTotal_andWeightsNotDecidedYet() {
        List<IdealMatchingService.Wanted> criteria = service.prepare(vacancy, profile(true,
                skill("Java", 40, MatchingRequirement.MUST_HAVE), skill("Kafka", null, MatchingRequirement.PREFERRED)));

        service.store(vacancy, criteria);

        verify(criteriaRepository).saveAllAndFlush(saved.capture());
        assertThat(saved.getValue()).extracting(JobScreeningCriteria::getWeight)
                .containsExactly(new BigDecimal("0.400"), null);
        assertThat(saved.getValue()).extracting(JobScreeningCriteria::getPosition).containsExactly(0, 1);
    }

    @Test
    void switchingMatchingOff_keepsTheCriteria_soSwitchingBackRestoresThem() {
        List<IdealMatchingService.Wanted> criteria = service.prepare(vacancy, profile(false,
                skill("Java", 100, MatchingRequirement.MUST_HAVE)));

        service.store(vacancy, criteria);

        assertThat(vacancy.isIdealMatchingEnabled()).isFalse();
        verify(criteriaRepository).saveAllAndFlush(saved.capture());
        assertThat(saved.getValue()).extracting(JobScreeningCriteria::getName).containsExactly("Java");
    }

    // ----------------------------------------------------------- completeness

    @Test
    void publishing_needsTheWeightsToTotalExactlyAHundred() {
        assertThatThrownBy(() -> IdealMatchingService.requireComplete(profile(true,
                skill("Java", 50, MatchingRequirement.MUST_HAVE), skill("Kafka", 35, MatchingRequirement.PREFERRED)),
                IdealMatchingService.NOT_READY_TO_PUBLISH))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("This vacancy can't be published yet: the ideal candidate weights add up to 85%; "
                        + "they need to total 100%.");
    }

    @Test
    void publishing_needsEveryCriterionWeighed_andAtLeastOne() {
        assertThatThrownBy(() -> IdealMatchingService.requireComplete(profile(true,
                skill("Java", 100, MatchingRequirement.MUST_HAVE), skill("Kafka", null, MatchingRequirement.PREFERRED)),
                IdealMatchingService.NOT_READY_TO_PUBLISH))
                .hasMessageContaining("give Kafka a weight or remove it");
        assertThatThrownBy(() -> IdealMatchingService.requireComplete(profile(true),
                IdealMatchingService.NOT_READY_TO_PUBLISH))
                .hasMessageContaining("weigh at least one skill for the ideal candidate");
    }

    @Test
    void publishing_passesWithMatchingOff_orAtExactlyAHundred() {
        assertThatCode(() -> IdealMatchingService.requireComplete(profile(false), IdealMatchingService.NOT_READY_TO_PUBLISH))
                .doesNotThrowAnyException();
        assertThatCode(() -> IdealMatchingService.requireComplete((IdealMatchingRequest) null, IdealMatchingService.NOT_READY_TO_PUBLISH))
                .doesNotThrowAnyException();
        assertThatCode(() -> IdealMatchingService.requireComplete(profile(true,
                skill("Java", 60, MatchingRequirement.MUST_HAVE), skill("Kafka", 40, MatchingRequirement.PREFERRED)),
                IdealMatchingService.NOT_READY_TO_PUBLISH))
                .doesNotThrowAnyException();
    }

    @Test
    void aStoredVacancy_isJudgedOnItsStoredCriteria() {
        service.prepare(vacancy, profile(true));
        storedCriteria(row(MatchingCategory.TECHNICAL_SKILL, "Java", "0.700", MatchingRequirement.MUST_HAVE, 0));

        assertThatThrownBy(() -> service.requireComplete(vacancy, IdealMatchingService.NOT_READY_TO_PUBLISH))
                .hasMessageContaining("add up to 70%");
    }

    @Test
    void replace_onALiveVacancy_mustStayAtAHundred() {
        JobVacancy live = VacancyFixtures.publishedOnPipeline(TENANT);
        live.offerIdealMatching(true);
        when(access.require(VIEWER, live.getId())).thenReturn(live);
        when(criteriaRepository.findAllByJobIdOrderByPositionAscNameAsc(live.getId())).thenReturn(List.of(),
                List.of(row(MatchingCategory.TECHNICAL_SKILL, "Java", "0.500", MatchingRequirement.MUST_HAVE, 0)));

        assertThatThrownBy(() -> service.replace(VIEWER, live.getId(),
                profile(true, skill("Java", 50, MatchingRequirement.MUST_HAVE))))
                .hasMessageStartingWith(IdealMatchingService.MUST_STAY_COMPLETE);
    }

    @Test
    void replace_refusesAClosedVacancy() {
        JobVacancy closed = VacancyFixtures.closed(TENANT);
        when(access.require(VIEWER, closed.getId())).thenReturn(closed);

        assertThatThrownBy(() -> service.replace(VIEWER, closed.getId(), profile(false)))
                .isInstanceOf(BusinessRuleException.class);
        verify(criteriaRepository, never()).saveAllAndFlush(any());
    }

    // ------------------------------------------------------------------ reads

    @Test
    void get_answersTheProfileWithWholePercentsAndTheTotal() {
        when(access.require(VIEWER, vacancy.getId())).thenReturn(vacancy);
        service.prepare(vacancy, profile(true));
        storedCriteria(
                row(MatchingCategory.TECHNICAL_SKILL, "Java", "0.600", MatchingRequirement.MUST_HAVE, 0),
                row(MatchingCategory.TECHNICAL_SKILL, "Kafka", "0.250", MatchingRequirement.PREFERRED, 1));

        IdealMatchingResponse response = service.get(VIEWER, vacancy.getId());

        assertThat(response.available()).isTrue();
        assertThat(response.criteria()).extracting(IdealMatchingResponse.Criterion::weightPercent).containsExactly(60, 25);
        assertThat(response.totalWeightPercent()).isEqualTo(85);
    }

    @Test
    void screeningProfile_weighsSkillsOnly_andHandsOverToolsExperienceAndEducationAsContext() {
        when(access.require(VIEWER, vacancy.getId())).thenReturn(vacancy);
        service.prepare(vacancy, profile(true));
        storedCriteria(
                row(MatchingCategory.TECHNICAL_SKILL, "Java", "0.750", MatchingRequirement.MUST_HAVE, 0),
                row(MatchingCategory.TECHNICAL_SKILL, "Kafka", "0.250", MatchingRequirement.PREFERRED, 1));

        ScreeningProfileResponse profile = service.screeningProfile(VIEWER, vacancy.getId());

        assertThat(profile.enabled()).isTrue();
        assertThat(profile.criteriaWeights()).containsExactly(
                new ScreeningProfileResponse.CriterionWeight("Java", 0.75),
                new ScreeningProfileResponse.CriterionWeight("Kafka", 0.25));
        assertThat(profile.mustHave()).containsExactly("Java");
        assertThat(profile.preferred()).containsExactly("Kafka");
        // Unweighted context, read from the vacancy's candidate requirements.
        assertThat(profile.toolsAndTechnologies()).containsExactly("Docker", "Git");
        assertThat(profile.minimumExperienceYears()).isEqualTo(4);
        assertThat(profile.acceptedDegreeTypes()).containsExactly("Computer Science");
        assertThat(profile.niceToHaveCertifications()).containsExactly("AWS Solutions Architect");
        assertThat(profile.educationLevel()).isEqualTo("Bachelor’s degree");
    }

    @Test
    void screeningProfile_withMatchingOff_sendsNoCriteria_soScreeningUsesItsDefaults() {
        when(access.require(VIEWER, vacancy.getId())).thenReturn(vacancy);

        ScreeningProfileResponse profile = service.screeningProfile(VIEWER, vacancy.getId());

        assertThat(profile.enabled()).isFalse();
        assertThat(profile.criteriaWeights()).isEmpty();
        verify(criteriaRepository, never()).findAllByJobIdOrderByPositionAscNameAsc(any());
    }

    // -------------------------------------------------------------- fixtures

    private static IdealMatchingRequest profile(boolean enabled, MatchingCriterionRequest... criteria) {
        return new IdealMatchingRequest(enabled, List.of(criteria), List.of("Computer Science", "computer science"),
                List.of("AWS Solutions Architect"));
    }

    private static MatchingCriterionRequest skill(String name, Integer weight, MatchingRequirement requirement) {
        return new MatchingCriterionRequest(MatchingCategory.TECHNICAL_SKILL, name, weight, requirement);
    }

    private JobScreeningCriteria row(MatchingCategory category, String name, String weight,
                                     MatchingRequirement requirement, int position) {
        return new JobScreeningCriteria(TENANT, vacancy.getId(), category, name, new BigDecimal(weight), requirement,
                position);
    }

    private void storedCriteria(JobScreeningCriteria... rows) {
        when(criteriaRepository.findAllByJobIdOrderByPositionAscNameAsc(vacancy.getId())).thenReturn(List.of(rows));
    }
}
