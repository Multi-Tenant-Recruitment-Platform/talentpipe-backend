package com.talentpipe.job.dto;

import com.talentpipe.job.entity.MatchingCategory;
import com.talentpipe.job.entity.MatchingRequirement;
import java.util.List;
import java.util.UUID;

/**
 * A vacancy's ideal candidate, as the job form and the vacancy page show it.
 * Company-side only: none of it is ever part of the public job board.
 *
 * @param available          whether the vacancy's pipeline template offers matching
 * @param totalWeightPercent the criteria's weights added up; 100 when complete
 */
public record IdealMatchingResponse(
        boolean available,
        boolean enabled,
        List<Criterion> criteria,
        int totalWeightPercent,
        List<String> degreeTypes,
        List<String> niceToHaveCertifications
) {

    /** One weighted criterion, in the order the recruiter put them. */
    public record Criterion(
            UUID id,
            MatchingCategory category,
            String name,
            int weightPercent,
            MatchingRequirement requirement,
            int position
    ) {
    }
}
