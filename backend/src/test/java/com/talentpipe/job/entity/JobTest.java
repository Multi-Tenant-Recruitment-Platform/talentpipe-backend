package com.talentpipe.job.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class JobTest {

    @Test
    void exposesStateAndUsesBaseIdentityEquality() {
        Job job = new Job(UUID.randomUUID(), "Title", "Description");

        assertThat(job.getTitle()).isEqualTo("Title");
        assertThat(job.getDescription()).isEqualTo("Description");
        assertThat(job.getStatus()).isEqualTo(JobStatus.PUBLISHED);
        assertThat(job).isSameAs(job);
        assertThat(job).hasSameHashCodeAs(job);

        job.setStatus(JobStatus.CLOSED);
        assertThat(job.getStatus()).isEqualTo(JobStatus.CLOSED);
    }
}
