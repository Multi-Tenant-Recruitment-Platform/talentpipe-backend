package com.talentpipe.job.entity;

import com.talentpipe.common.exception.BusinessRuleException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * What an advert must contain before candidates may see it.
 *
 * <p>A draft is a partial vacancy by definition and is saved without any of
 * this. The rules apply at the three moments an advert is, or is about to be,
 * public: publishing a draft, creating a vacancy straight to PUBLISHED, and
 * editing one that is already live. They mirror {@code validateVacancy} in the
 * frontend's {@code jobVacancy.ts}, which normally catches the same gaps first
 * — but the server is the authority.</p>
 *
 * <p>A failure is a 422 carrying one sentence written for the recruiter, since
 * the frontend shows it word for word:
 * <em>"This vacancy can't be published yet: add an application deadline, a job
 * summary and at least one required skill."</em></p>
 *
 * <p>Shape rules (lengths, ranges, enum membership) are not checked here; they
 * hold in every state and are enforced on the request before this runs.</p>
 */
public final class VacancyPublishRules {

    /** Opening of the refusal when a draft is not ready to go live. */
    static final String NOT_READY_TO_PUBLISH = "This vacancy can't be published yet";

    /** Opening of the refusal when an edit would leave a live advert incomplete. */
    static final String MUST_STAY_COMPLETE = "A published vacancy has to stay complete";

    private VacancyPublishRules() {
        // static rules only
    }

    /**
     * What {@code content} still lacks, in the reading order of the vacancy
     * form, each phrased to follow the word "add".
     *
     * @param today the current date in the tenant's timezone; a deadline
     *              earlier than this has already passed
     * @return the missing items, empty when the advert is complete
     */
    public static List<String> missing(VacancyContent content, LocalDate today) {
        List<String> missing = new ArrayList<>();

        if (content.title().isBlank()) {
            missing.add("a job title");
        }
        if (content.department().isBlank()) {
            missing.add("a department");
        }
        if (content.employmentType() == null) {
            missing.add("an employment type");
        }
        if (content.workplaceType() == null) {
            missing.add("a workplace type");
        }
        if (content.location().isBlank()) {
            missing.add("a location");
        }
        if (content.applicationDeadline() == null) {
            missing.add("an application deadline");
        } else if (content.applicationDeadline().isBefore(today)) {
            // The deadline day itself still counts: applications are accepted
            // through the end of it.
            missing.add("an application deadline that is today or later");
        }
        if (content.jobSummary().isBlank()) {
            missing.add("a job summary");
        }
        if (content.jobDescription().isBlank()) {
            missing.add("a job description");
        }
        if (content.keyResponsibilities().isEmpty()) {
            missing.add("at least one key responsibility");
        }
        if (content.requiredSkills().isEmpty()) {
            missing.add("at least one required skill");
        }
        return missing;
    }

    /**
     * Refuses an advert that is not complete enough to be public.
     *
     * @param lead how the sentence opens — {@link #NOT_READY_TO_PUBLISH} or
     *             {@link #MUST_STAY_COMPLETE}
     * @throws BusinessRuleException (422) naming everything that is missing
     */
    static void requireComplete(VacancyContent content, LocalDate today, String lead) {
        List<String> missing = missing(content, today);
        if (!missing.isEmpty()) {
            throw new BusinessRuleException(lead + ": add " + joinForSentence(missing) + ".");
        }
    }

    /** {@code [a, b, c]} → {@code "a, b and c"}. */
    private static String joinForSentence(List<String> items) {
        if (items.size() == 1) {
            return items.get(0);
        }
        String allButLast = String.join(", ", items.subList(0, items.size() - 1));
        return allButLast + " and " + items.get(items.size() - 1);
    }
}
