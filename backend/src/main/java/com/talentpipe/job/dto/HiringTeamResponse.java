package com.talentpipe.job.dto;

import com.talentpipe.auth.dto.MemberSummary;
import java.util.List;
import java.util.UUID;

/**
 * Everyone who works on a vacancy, as the hiring-team panel shows it.
 *
 * @param recruiter     the assigned recruiter; {@code null} when none is set
 * @param hiringManager the hiring manager; {@code null} when none is set
 * @param members       the rest of the hiring team, in the order they were added
 */
public record HiringTeamResponse(
        UUID vacancyId,
        MemberSummary recruiter,
        MemberSummary hiringManager,
        List<MemberSummary> members
) {
}
