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
 * @param category      always a skill since V28; tools and experience are not weighted
 * @param name          the skill, e.g. "Java"
 * @param weightPercent its share of the 100%, as a whole percent; {@code null}
 *                      while a draft has not decided it yet
 * @param requirement   must-have or preferred
 */
public record MatchingCriterionRequest(

        @NotNull(message = "Say what kind of criterion each one is.")
        MatchingCategory category,

        @Size(max = 120, message = "Keep each criterion's name to 120 characters or fewer.")
        String name,

        @Min(value = 1, message = "Give each criterion a weight of at least 1%.")
        @Max(value = 100, message = "A criterion can weigh at most 100%.")
        Integer weightPercent,

        MatchingRequirement requirement
) {
}
