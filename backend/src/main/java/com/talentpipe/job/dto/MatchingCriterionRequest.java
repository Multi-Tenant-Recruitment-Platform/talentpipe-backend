package com.talentpipe.job.dto;

import com.talentpipe.job.entity.MatchingCategory;
import com.talentpipe.job.entity.MatchingRequirement;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * One weighted criterion of a vacancy's ideal candidate, as the job form sends it.
 *
 * @param category      a technical skill, a tool or relevant experience
 * @param name          what it is, e.g. "Docker"; experience may leave it out
 * @param weightPercent its share of the 100%, as a whole percent; {@code null}
 *                      while a draft has not decided it yet
 * @param requirement   must-have or preferred, for skills and tools; left out for experience
 */
public record MatchingCriterionRequest(

        @NotNull(message = "Say whether each criterion is a skill, a tool or experience.")
        MatchingCategory category,

        @Size(max = 120, message = "Keep each criterion's name to 120 characters or fewer.")
        String name,

        @Min(value = 1, message = "Give each criterion a weight of at least 1%.")
        @Max(value = 100, message = "A criterion can weigh at most 100%.")
        Integer weightPercent,

        MatchingRequirement requirement
) {
}
