package com.talentpipe.team.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record MapVacancyRequest(
        @NotNull(message = "Vacancy ID is required")
        UUID vacancyId
) {
}
