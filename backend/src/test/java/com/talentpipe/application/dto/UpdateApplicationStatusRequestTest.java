package com.talentpipe.application.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.talentpipe.application.entity.ApplicationStatus;
import org.junit.jupiter.api.Test;

class UpdateApplicationStatusRequestTest {

    @Test
    void storesRequestedStatus() {
        UpdateApplicationStatusRequest request =
                new UpdateApplicationStatusRequest(ApplicationStatus.IN_REVIEW);

        assertThat(request.status()).isEqualTo(ApplicationStatus.IN_REVIEW);
    }
}
