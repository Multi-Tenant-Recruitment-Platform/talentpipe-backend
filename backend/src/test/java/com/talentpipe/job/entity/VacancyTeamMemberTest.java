package com.talentpipe.job.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Unit tests for the hiring-team row's composite key: one row per person per vacancy. */
class VacancyTeamMemberTest {

    private static final UUID VACANCY = UUID.randomUUID();
    private static final UUID PERSON = UUID.randomUUID();

    @Test
    void keysOfTheSamePersonOnTheSameVacancy_areEqual() {
        VacancyTeamMember.Key key = new VacancyTeamMember.Key(VACANCY, PERSON);

        assertThat(key).isEqualTo(new VacancyTeamMember.Key(VACANCY, PERSON))
                .hasSameHashCodeAs(new VacancyTeamMember.Key(VACANCY, PERSON))
                .isEqualTo(key);
    }

    @Test
    void keysDifferingInEitherPart_areNotEqual() {
        VacancyTeamMember.Key key = new VacancyTeamMember.Key(VACANCY, PERSON);

        assertThat(key).isNotEqualTo(new VacancyTeamMember.Key(VACANCY, UUID.randomUUID()))
                .isNotEqualTo(new VacancyTeamMember.Key(UUID.randomUUID(), PERSON))
                .isNotEqualTo("not a key");
    }

    @Test
    void aMember_remembersWhatItWasCreatedWith() {
        UUID tenant = UUID.randomUUID();

        VacancyTeamMember member = new VacancyTeamMember(VACANCY, PERSON, tenant, UUID.randomUUID());

        assertThat(member.getVacancyId()).isEqualTo(VACANCY);
        assertThat(member.getUserId()).isEqualTo(PERSON);
        assertThat(member.getTenantId()).isEqualTo(tenant);
    }
}
