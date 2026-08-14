package com.talentpipe.team.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record CreateTeamRequest(
        @NotBlank(message = "Team name is required")
        @Size(max = 150, message = "Team name cannot exceed 150 characters")
        String name,

        @Size(max = 2000, message = "Description cannot exceed 2000 characters")
        String description,

        @Size(max = 100, message = "Department cannot exceed 100 characters")
        String department,

        UUID leadId
) {
}
