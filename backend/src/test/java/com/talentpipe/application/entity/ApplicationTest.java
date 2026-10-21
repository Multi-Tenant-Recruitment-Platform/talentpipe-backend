package com.talentpipe.application.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class ApplicationTest {

    @Test
    void changesStatusAndTracksWithdrawalTime() {
        Application application = new Application(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
        Application anotherApplication =
                new Application(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());

        assertThat(application.getStatus()).isEqualTo(ApplicationStatus.APPLIED);
        assertThat(application).isNotEqualTo(anotherApplication);
        application.changeStatus(ApplicationStatus.WITHDRAWN);
        assertThat(application.getWithdrawnAt()).isNotNull();
        application.changeStatus(ApplicationStatus.REJECTED);
        assertThat(application.getWithdrawnAt()).isNull();
    }
}
