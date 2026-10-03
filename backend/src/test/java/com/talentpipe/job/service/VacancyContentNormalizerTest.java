package com.talentpipe.job.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.talentpipe.common.exception.InvalidRequestException;
import com.talentpipe.common.util.HtmlSanitizer;
import com.talentpipe.job.VacancyFixtures;
import com.talentpipe.job.dto.JobVacancyRequest;
import com.talentpipe.job.entity.VacancyContent;
import com.talentpipe.job.entity.VacancyStatus;
import com.talentpipe.job.entity.WeekDay;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link VacancyContentNormalizer}, run against the real
 * {@link HtmlSanitizer} — the point is what actually reaches the database.
 */
class VacancyContentNormalizerTest {

    private final VacancyContentNormalizer normalizer = new VacancyContentNormalizer(new HtmlSanitizer());

    private VacancyContent normalize(VacancyFixtures.ContentBuilder builder) {
        return normalizer.normalize(builder.toRequest(VacancyStatus.DRAFT, null));
    }

    // ------------------------------------------------------------- plain text

    @Test
    void singleLineFields_areTrimmedAndInnerWhitespaceCollapsed() {
        VacancyContent content = normalize(VacancyFixtures.complete()
                .title("  Senior   Backend\tEngineer  ")
                .department(" Engineering ")
                .location("Colombo,   Sri Lanka"));

        assertThat(content.title()).isEqualTo("Senior Backend Engineer");
        assertThat(content.department()).isEqualTo("Engineering");
        assertThat(content.location()).isEqualTo("Colombo, Sri Lanka");
    }

    @Test
    void longTextFields_keepTheirLineBreaks() {
        VacancyContent content = normalize(VacancyFixtures.complete()
                .jobDescription("  First paragraph.\n\nSecond paragraph.  "));

        assertThat(content.jobDescription()).isEqualTo("First paragraph.\n\nSecond paragraph.");
    }

    @Test
    void nullBasics_becomeEmptyStrings_notNulls() {
        VacancyContent content = normalize(VacancyFixtures.complete()
                .title(null).department(null).location(null).jobSummary(null).jobDescription(null));

        assertThat(content.title()).isEmpty();
        assertThat(content.department()).isEmpty();
        assertThat(content.location()).isEmpty();
        assertThat(content.jobSummary()).isEmpty();
        assertThat(content.jobDescription()).isEmpty();
    }

    @Test
    void blankOptionalText_becomesNull() {
        VacancyContent content = normalize(VacancyFixtures.complete().currency("   ").recruitmentPipelineId(" "));

        assertThat(content.currency()).isNull();
        assertThat(content.recruitmentPipelineId()).isNull();
    }

    @Test
    void pipelineId_isUpperCased_itIsAnIdentifierNotProse() {
        VacancyContent content = normalize(VacancyFixtures.complete().recruitmentPipelineId(" technical "));

        assertThat(content.recruitmentPipelineId()).isEqualTo("TECHNICAL");
    }

    @Test
    void missingOpenings_fallBackToOne() {
        JobVacancyRequest withoutOpenings = new JobVacancyRequest(
                "Title", null, null, null, null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null, null, null, null, null, null,
                null, VacancyStatus.DRAFT, null);

        assertThat(normalizer.normalize(withoutOpenings).openings()).isEqualTo(1);
    }

    // ------------------------------------------------------------ stored XSS

    @Test
    void htmlIsStrippedFromEveryFreeTextField() {
        VacancyContent content = normalize(VacancyFixtures.complete()
                .title("<b>Engineer</b>")
                .jobSummary("<script>alert('x')</script>Build things.")
                .jobDescription("<img src=x onerror=alert(1)>Own the service.")
                .requiredSkills(List.of("<i>Java</i>")));

        assertThat(content.title()).isEqualTo("Engineer");
        assertThat(content.jobSummary()).isEqualTo("Build things.");
        assertThat(content.jobDescription()).isEqualTo("Own the service.");
        assertThat(content.requiredSkills()).containsExactly("Java");
    }

    @Test
    void textThatIsNothingButMarkup_normalizesToEmpty_soPublishRulesSeeItAsMissing() {
        VacancyContent content = normalize(VacancyFixtures.complete().title("<b></b>"));

        assertThat(content.title()).isEmpty();
    }

    @Test
    void ordinaryPunctuationSurvivesUnescaped() {
        VacancyContent content = normalize(VacancyFixtures.complete()
                .title("R&D Engineer (C++ / C#)").jobSummary("Ben & Jerry's \"best\" team"));

        assertThat(content.title()).isEqualTo("R&D Engineer (C++ / C#)");
        assertThat(content.jobSummary()).isEqualTo("Ben & Jerry's \"best\" team");
    }

    // ------------------------------------------------------------------ lists

    @Test
    void lists_dropBlanks_trimEntries_andDeduplicateIgnoringCase() {
        VacancyContent content = normalize(VacancyFixtures.complete()
                .requiredSkills(Arrays.asList("  Java ", "", "java", null, "Spring   Boot", "JAVA")));

        assertThat(content.requiredSkills()).containsExactly("Java", "Spring Boot");
    }

    @Test
    void nullList_becomesEmpty() {
        VacancyContent content = normalize(VacancyFixtures.complete().keyResponsibilities(null));

        assertThat(content.keyResponsibilities()).isEmpty();
    }

    @Test
    void workingDays_areDeduplicatedAndPutInWeekOrder() {
        VacancyContent content = normalize(VacancyFixtures.complete()
                .workingDays(Arrays.asList(WeekDay.FRI, WeekDay.MON, null, WeekDay.WED, WeekDay.MON)));

        assertThat(content.workingDays()).containsExactly(WeekDay.MON, WeekDay.WED, WeekDay.FRI);
    }

    // --------------------------------------------------------------- benefits

    @Test
    void benefits_areRewrittenToTheirCanonicalCatalogueIds() {
        VacancyContent content = normalize(VacancyFixtures.complete()
                .benefits(List.of("health_insurance", "Flexible-Hours", "HEALTH_INSURANCE")));

        assertThat(content.benefits()).containsExactly("HEALTH_INSURANCE", "FLEXIBLE_HOURS");
    }

    @Test
    void benefits_outsideTheCatalogue_areRejectedAsABadRequest() {
        VacancyFixtures.ContentBuilder builder = VacancyFixtures.complete().benefits(List.of("Free coffee"));

        assertThatThrownBy(() -> normalize(builder))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessage("Choose benefits from the benefits catalogue only.");
    }
}
