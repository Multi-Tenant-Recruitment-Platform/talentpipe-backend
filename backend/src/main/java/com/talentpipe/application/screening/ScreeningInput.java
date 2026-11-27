package com.talentpipe.application.screening;

import java.util.List;
import java.util.UUID;

/**
 * Everything a screener needs to score one application, gathered before the
 * (possibly slow) call so that no database transaction is held open during it.
 *
 * @param applicationId which application this is, for a screener that needs a stable seed
 * @param cvText        the CV's extracted text; null when there is none yet
 * @param jobDescription the advert's description
 * @param criteria      what to score on: the vacancy's ideal-matching skills, or its required
 *                      and preferred skills when it has none
 * @param blindMode     whether the screener must ignore who the candidate is
 */
public record ScreeningInput(UUID applicationId, String cvText, String jobDescription,
                             List<Criterion> criteria, boolean blindMode) {

    public ScreeningInput {
        criteria = criteria == null ? List.of() : List.copyOf(criteria);
    }

    public boolean hasCvText() {
        return cvText != null && !cvText.isBlank();
    }

    /**
     * One thing to score on.
     *
     * @param weight   its share of the score, 0 to 1
     * @param mustHave whether the vacancy requires it rather than prefers it
     */
    public record Criterion(String name, double weight, boolean mustHave) {
    }
}
