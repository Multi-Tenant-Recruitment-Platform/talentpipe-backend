package com.talentpipe.job.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

/**
 * The panel of one interview round, as a create, an edit or
 * {@code PUT /jobs/{id}/interview-panels} sends it.
 *
 * <p>The round is named by its position in the pipeline rather than by stage
 * id, because a new vacancy's stages have no ids until it is stored. A
 * position is unique within a vacancy and never changes (V23).</p>
 *
 * <p>A round with no count and nobody chosen has no panel yet; that is fine on
 * a draft. Choosing people needs a count first, and never more people than
 * it. Whether the panel is complete is checked when the vacancy is published.</p>
 *
 * @param stagePosition    the round's position in the vacancy's pipeline
 * @param interviewerCount how many interviewers the round needs; {@code null} when not decided yet
 * @param interviewerIds   active company members; repeats are ignored. {@code null} is an empty list
 */
public record InterviewPanelRequest(

        @NotNull(message = "Say which interview round each panel is for.")
        Integer stagePosition,

        @Min(value = 1, message = "An interview round needs at least one interviewer.")
        @Max(value = InterviewPanelRequest.MAX_INTERVIEWERS,
                message = "An interview round can have at most " + InterviewPanelRequest.MAX_INTERVIEWERS
                        + " interviewers.")
        Integer interviewerCount,

        @Size(max = InterviewPanelRequest.MAX_INTERVIEWERS,
                message = "An interview round can have at most " + InterviewPanelRequest.MAX_INTERVIEWERS
                        + " interviewers.")
        List<@NotNull(message = "Choose a person for every place on the panel.") UUID> interviewerIds
) {

    /** The most interviewers one round may have; the V25 check says the same. */
    public static final int MAX_INTERVIEWERS = 10;

    /** {@link #interviewerIds}, never {@code null}. */
    public List<UUID> interviewers() {
        return interviewerIds == null ? List.of() : interviewerIds;
    }
}
