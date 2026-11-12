package com.talentpipe.job.entity;

import java.util.Objects;
import java.util.UUID;

/**
 * The pipeline a new vacancy is put on, as {@link JobVacancy#runOn} stores it:
 * the template it came from, the screening settings resolved against that
 * template, and the shape of the stages copied from it (V23, V24).
 *
 * <p>Every value is frozen at creation. The template name in particular is
 * the name the template had then, so renaming or deleting the template later
 * changes nothing the vacancy reports.</p>
 *
 * @param stageCount          how many stages were copied
 * @param interviewRoundCount how many of them are interview rounds
 */
public record VacancyPipeline(UUID templateId, String templateName, int autoThreshold, int manualThreshold,
                              boolean blindModeEnabled, int stageCount, int interviewRoundCount) {

    public VacancyPipeline {
        Objects.requireNonNull(templateId, "templateId");
        Objects.requireNonNull(templateName, "templateName");
    }
}
