package com.talentpipe.job.service;

import com.talentpipe.job.dto.JobVacancyRequest;
import java.util.UUID;

/**
 * The pipeline a recruiter chose for a new vacancy: a template, and optionally
 * their own screening settings in place of the template's.
 *
 * @param templateId       required — a vacancy always runs on a template
 * @param autoThreshold    {@code null} takes the template's default
 * @param manualThreshold  {@code null} takes the template's default
 * @param blindModeEnabled {@code null} takes the template's setting
 */
public record PipelineSelection(UUID templateId, Integer autoThreshold, Integer manualThreshold,
                                Boolean blindModeEnabled) {

    /** What a create request chose. */
    public static PipelineSelection of(JobVacancyRequest request) {
        return new PipelineSelection(request.pipelineTemplateId(), request.autoThreshold(),
                request.manualThreshold(), request.blindModeEnabled());
    }
}
