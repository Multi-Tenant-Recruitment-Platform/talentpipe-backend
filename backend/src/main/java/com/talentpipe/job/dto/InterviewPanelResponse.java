package com.talentpipe.job.dto;

import com.talentpipe.auth.dto.MemberSummary;
import java.util.List;
import java.util.UUID;

/**
 * One interview round of a vacancy and the panel that runs it. Company-side
 * only: nothing here ever reaches the public job board or a candidate.
 *
 * @param stageId          the round's stage, as {@code GET /jobs/{id}/stages} lists it
 * @param position         its place in the pipeline; what a request names it by
 * @param benchmark        the round's pass mark
 * @param interviewerCount how many interviewers the round needs; {@code null} until decided
 * @param interviewers     who they are, in the order they were added. Someone
 *                         who has since been disabled stays listed until they
 *                         are taken off, with their status saying so
 */
public record InterviewPanelResponse(
        UUID stageId,
        int position,
        String name,
        Integer benchmark,
        Integer interviewerCount,
        List<MemberSummary> interviewers
) {
}
