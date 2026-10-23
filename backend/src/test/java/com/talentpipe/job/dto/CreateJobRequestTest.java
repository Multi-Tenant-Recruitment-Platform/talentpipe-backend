package com.talentpipe.job.dto;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CreateJobRequestTest {

    @Test
    void storesTitleAndDescription() {
        CreateJobRequest request = new CreateJobRequest("Title", "Description");

        assertThat(request.title()).isEqualTo("Title");
        assertThat(request.description()).isEqualTo("Description");
    }
}
