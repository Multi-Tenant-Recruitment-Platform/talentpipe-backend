package com.talentpipe.job.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * Every interview panel of a vacancy, replacing the ones stored — the body of
 * {@code PUT /jobs/{id}/interview-panels}. A round left out loses its panel;
 * send an empty list to clear them all.
 */
public record InterviewPanelsRequest(

        @NotNull(message = "List the interview panels, or send an empty list.")
        @Size(max = 30, message = "A vacancy has at most 30 interview rounds to set panels for.")
        List<@NotNull(message = "Send a panel for each interview round listed.") @Valid InterviewPanelRequest>
                panels
) {
}
