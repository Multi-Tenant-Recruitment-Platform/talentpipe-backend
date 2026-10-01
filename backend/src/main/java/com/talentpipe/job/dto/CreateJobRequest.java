package com.talentpipe.job.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateJobRequest(
        @NotBlank @Size(max = 200) String title,
        @NotBlank String description
) {
}
