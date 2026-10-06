package com.talentpipe.job.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.talentpipe.common.dto.PageResponse;
import com.talentpipe.job.dto.JobSummaryResponse;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PublicJobControllerTest {

    private final PublicJobController controller = new PublicJobController();

    @Test
    void listPublishedJobs_returnsEmptyPageResponse() {
        PageResponse<JobSummaryResponse> response = controller.listPublishedJobs(0, 20);

        assertThat(response).isNotNull();
        assertThat(response.content()).isEmpty();
        assertThat(response.page()).isEqualTo(0);
        assertThat(response.size()).isEqualTo(20);
        assertThat(response.totalElements()).isEqualTo(0);
        assertThat(response.totalPages()).isEqualTo(0);
    }

    @Test
    void jobSummaryResponse_recordProperties() {
        UUID id = UUID.randomUUID();
        JobSummaryResponse summary = new JobSummaryResponse(id, "Software Engineer", "Acme Corp");

        assertThat(summary.id()).isEqualTo(id);
        assertThat(summary.title()).isEqualTo("Software Engineer");
        assertThat(summary.companyName()).isEqualTo("Acme Corp");
    }
}
