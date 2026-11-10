package com.talentpipe.job.service;

import com.talentpipe.common.exception.BusinessRuleException;
import com.talentpipe.common.exception.InvalidRequestException;
import com.talentpipe.common.exception.ResourceNotFoundException;
import com.talentpipe.job.dto.JobStageResponse;
import com.talentpipe.job.entity.JobVacancy;
import java.util.List;
import java.util.UUID;

/**
 * Puts new vacancies on a pipeline: reads the chosen template, resolves the
 * vacancy's screening settings against it, and copies the template's stages
 * to the vacancy. From then on the vacancy runs on its own copy, so editing,
 * archiving or deleting the template never changes it (PT-7, PT-8).
 *
 * <p>Creating a vacancy calls {@link #resolve} before the vacancy is stored and
 * {@link #copyStages} straight after, inside the same transaction: the vacancy
 * and all of its stages are stored together or not at all.</p>
 */
public interface PipelineSnapshotService {

    /**
     * Reads the template and works out the settings the vacancy starts with: a
     * threshold or blind mode the selection leaves out is the template's. Writes
     * nothing.
     *
     * @throws InvalidRequestException   (400) no template chosen
     * @throws ResourceNotFoundException (404) the template does not exist or is another company's
     * @throws BusinessRuleException     (422) the template is archived, or the resolved
     *                                   manual threshold is not below the auto threshold
     */
    PipelineSnapshot resolve(UUID tenantId, PipelineSelection selection);

    /**
     * Copies the snapshot's stages onto a vacancy that has just been stored.
     * Must run in the transaction that stored the vacancy.
     */
    void copyStages(JobVacancy vacancy, PipelineSnapshot snapshot);

    /**
     * The stages the vacancy runs on, first to last.
     *
     * @throws ResourceNotFoundException (404) missing, another company's, or not the viewer's to see
     */
    List<JobStageResponse> stages(VacancyViewer viewer, UUID vacancyId);
}
