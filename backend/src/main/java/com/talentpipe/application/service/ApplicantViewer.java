package com.talentpipe.application.service;

import java.util.UUID;

/**
 * A company member looking at applicants, as the controller found them in
 * the request: who they are, and whether their role sees every applicant
 * ({@code applications.view.all}) or only those to vacancies they work on.
 */
public record ApplicantViewer(UUID tenantId, UUID userId, boolean seesAllApplicants) {
}
