package com.talentpipe.job.entity;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Where a vacancy is in its life, and the only moves it may make from there
 * (PB-018 → PB-022). Mirrors the CHECK constraint on {@code job_vacancies.status}.
 *
 * <pre>
 *   DRAFT ──publish──▶ PUBLISHED ──close──▶ CLOSED ──archive──▶ ARCHIVED
 * </pre>
 *
 * <ul>
 *   <li>{@link #DRAFT} — visible only inside the workspace; may be incomplete.</li>
 *   <li>{@link #PUBLISHED} — on the candidate portal and accepting applications.</li>
 *   <li>{@link #CLOSED} — off the portal, takes no new applications; received
 *       applications are kept.</li>
 *   <li>{@link #ARCHIVED} — out of every active list, retained for reporting.</li>
 * </ul>
 *
 * <p>The machine is deliberately one-way. The backlog defines publishing,
 * closing and archiving but not their inverses, so there is no reopen,
 * unpublish or unarchive. {@link #TRANSITIONS} is the single place to change
 * if that is ever agreed: {@link #canTransitionTo}, the refusal wording in
 * {@link #entryRule()} and every guard in {@link JobVacancy} read from it.
 * The frontend keeps the same table as {@code VACANCY_TRANSITIONS}.</p>
 */
public enum VacancyStatus {

    DRAFT,
    PUBLISHED,
    CLOSED,
    ARCHIVED;

    /** Every legal move, and nothing else: source state → the states it may enter. */
    private static final Map<VacancyStatus, Set<VacancyStatus>> TRANSITIONS = Map.of(
            DRAFT, Set.of(PUBLISHED),
            PUBLISHED, Set.of(CLOSED),
            CLOSED, Set.of(ARCHIVED),
            ARCHIVED, Set.of());

    /** @return true if a vacancy in this state may move directly to {@code target} */
    public boolean canTransitionTo(VacancyStatus target) {
        return TRANSITIONS.get(this).contains(target);
    }

    /** @return the states directly reachable from this one; empty for a terminal state */
    public Set<VacancyStatus> nextStatuses() {
        return TRANSITIONS.get(this);
    }

    /**
     * Whether the advert's content may still change. Once closed, the advert is
     * the record of what candidates applied to, and rewriting it would change
     * history under them.
     */
    public boolean isEditable() {
        return this == DRAFT || this == PUBLISHED;
    }

    /** Whether the vacancy is shown on the public candidate portal. */
    public boolean isPubliclyListed() {
        return this == PUBLISHED;
    }

    /**
     * The rule for entering this state, as a sentence a recruiter can read —
     * e.g. {@code "Only a draft vacancy can be published."}. Derived from
     * {@link #TRANSITIONS} so the wording can never disagree with the table.
     */
    public String entryRule() {
        List<String> sources = Arrays.stream(values())
                .filter(source -> source.canTransitionTo(this))
                .map(VacancyStatus::adjective)
                .toList();
        if (sources.isEmpty()) {
            return "A vacancy can't be moved back to " + adjective() + ".";
        }
        return "Only a " + String.join(" or ", sources) + " vacancy can be " + adjective() + ".";
    }

    /** This state as it reads after "This vacancy is …": "a draft", "published", "closed", "archived". */
    public String description() {
        return this == DRAFT ? "a draft" : adjective();
    }

    private String adjective() {
        return name().toLowerCase(Locale.ROOT);
    }
}
