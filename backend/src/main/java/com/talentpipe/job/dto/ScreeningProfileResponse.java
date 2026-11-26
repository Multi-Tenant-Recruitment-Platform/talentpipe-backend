package com.talentpipe.job.dto;

import java.util.List;
import java.util.UUID;

/**
 * What AI screening needs to score this vacancy's applicants: the hand-off
 * from the job module to screening (MRP-361). Nothing here calls the AI
 * service; this is the data it will be sent.
 *
 * <p>{@code criteriaWeights} is exactly the AI service's {@code criteria_weights}
 * — a name and a fraction above 0 and at most 1, the fractions adding up to 1.
 * The rest is context the prompt can use: which criteria are must-haves, the
 * education and degree types asked for, and nice-to-have certifications, none
 * of them weighted. With matching off, {@code criteriaWeights} is empty and
 * screening falls back to its default dimensions.</p>
 *
 * @param enabled whether the vacancy screens against its own weighted profile
 */
public record ScreeningProfileResponse(
        UUID vacancyId,
        boolean enabled,
        boolean blindMode,
        String jobTitle,
        String jobSummary,
        String jobDescription,
        List<CriterionWeight> criteriaWeights,
        List<String> mustHave,
        List<String> preferred,
        Integer minimumExperienceYears,
        String educationLevel,
        List<String> acceptedDegreeTypes,
        List<String> niceToHaveCertifications
) {

    /** One criterion and its share, as the AI service's {@code CriterionWeight}. */
    public record CriterionWeight(String name, double weight) {
    }
}
