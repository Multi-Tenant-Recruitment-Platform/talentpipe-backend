package com.talentpipe.job.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

/**
 * The whole hiring team of a vacancy, replacing the one stored. Send an empty
 * list to clear it. The assigned recruiter and hiring manager are set on the
 * vacancy itself and are not part of this list.
 *
 * @param memberIds workspace members; repeats are ignored
 */
public record HiringTeamRequest(

        @NotNull(message = "List the people on the hiring team, or send an empty list.")
        @Size(max = HiringTeamRequest.MAX_MEMBERS,
                message = "A hiring team can have at most " + HiringTeamRequest.MAX_MEMBERS + " people.")
        List<@NotNull(message = "Choose a person for every place on the team.") UUID> memberIds
) {

    public static final int MAX_MEMBERS = 50;
}
