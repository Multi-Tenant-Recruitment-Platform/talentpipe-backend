package com.talentpipe.job.dto;

import com.talentpipe.common.util.ValidTaxonomyList;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * A vacancy's ideal candidate, replacing the one stored — sent with a create
 * or an edit, or on its own to {@code PUT /jobs/{id}/ideal-matching}.
 *
 * <p>The criteria share one 100%, in the order listed. A draft may hold any
 * total; publishing needs exactly 100%. Degree types and nice-to-have
 * certifications are never weighted.</p>
 *
 * @param enabled                  screen applicants against this profile;
 *                                 only where the pipeline template offers it
 * @param criteria                 skills, tools and experience, each with its share
 * @param degreeTypes              accepted degree types, when the education asked for is a degree
 * @param niceToHaveCertifications certifications that count in a candidate's favour
 */
public record IdealMatchingRequest(

        boolean enabled,

        @Size(max = IdealMatchingRequest.MAX_CRITERIA,
                message = "Weigh at most " + IdealMatchingRequest.MAX_CRITERIA + " criteria.")
        List<@NotNull(message = "Send every criterion in full.") @Valid MatchingCriterionRequest> criteria,

        @ValidTaxonomyList(maxSize = 20, maxEntryLength = 120,
                message = "List up to 20 degree types, each 120 characters or fewer.")
        List<String> degreeTypes,

        @ValidTaxonomyList(maxSize = 20, maxEntryLength = 150,
                message = "List up to 20 certifications, each 150 characters or fewer.")
        List<String> niceToHaveCertifications
) {

    /** The most weighted criteria one vacancy may have. */
    public static final int MAX_CRITERIA = 60;

    /** {@link #criteria}, never {@code null}. */
    public List<MatchingCriterionRequest> criteriaOrEmpty() {
        return criteria == null ? List.of() : criteria;
    }
}
