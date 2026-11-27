package com.talentpipe.application.screening;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.talentpipe.application.entity.ScreeningResult;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class StubApplicationScreenerTest {

    private final StubApplicationScreener screener = new StubApplicationScreener(new MockEnvironment());

    private static ScreeningInput input(String cvText, List<ScreeningInput.Criterion> criteria) {
        return new ScreeningInput(UUID.fromString("7f8a0c2e-1b3d-4e5f-9a6b-0c1d2e3f4a5b"), cvText,
                "Backend role", criteria, false);
    }

    private static List<ScreeningInput.Criterion> javaAndKafka() {
        return List.of(new ScreeningInput.Criterion("Java", 0.75, true),
                new ScreeningInput.Criterion("Kafka", 0.25, false));
    }

    @Test
    void withCvText_scoresTheWeightedShareOfSkillsItMentions() {
        ScreeningResult result = screener.screen(input("Seven years of Java and Spring Boot.", javaAndKafka()));

        assertThat(result.overallScore()).isEqualTo(75);
        assertThat(result.matchedSkills()).containsExactly("Java");
        assertThat(result.missingSkills()).containsExactly("Kafka");
        assertThat(result.modelVersion()).isEqualTo(StubApplicationScreener.MODEL_VERSION);
        assertThat(result.summary()).contains("not an AI assessment");
    }

    @Test
    void aSkill_isMatchedAsAWholeWord_notInsideAnother() {
        ScreeningResult result = screener.screen(input("Five years of JavaScript.", javaAndKafka()));

        assertThat(result.matchedSkills()).isEmpty();
        assertThat(result.overallScore()).isZero();
    }

    @Test
    void withoutCvText_theDemoScoreIsTheSameEveryTime_andSaysItIsADemo() {
        ScreeningResult first = screener.screen(input(null, javaAndKafka()));
        ScreeningResult again = screener.screen(input(null, javaAndKafka()));

        assertThat(again.overallScore()).isEqualTo(first.overallScore());
        assertThat(again.matchedSkills()).isEqualTo(first.matchedSkills());
        assertThat(first.summary()).startsWith("Demo score");
    }

    @Test
    void aVacancyWithNoSkills_cannotBeScreened() {
        assertThatThrownBy(() -> screener.screen(input("Java", List.of())))
                .isInstanceOf(ScreeningUnavailableException.class);
    }

    @Test
    void itRefusesToRun_underAProductionProfile() {
        MockEnvironment production = new MockEnvironment();
        production.setActiveProfiles("prod");

        assertThatThrownBy(() -> new StubApplicationScreener(production)).isInstanceOf(IllegalStateException.class);
    }
}
