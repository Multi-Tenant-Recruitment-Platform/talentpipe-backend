package com.talentpipe.application.dto;

import com.talentpipe.application.entity.ApplicationStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateApplicationStatusRequest(@NotNull ApplicationStatus status) {
}
