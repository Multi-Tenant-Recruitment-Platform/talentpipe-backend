package com.talentpipe.job.service;

import java.util.UUID;

/**
 * Who is asking for vacancies, and how far they can see.
 *
 * <p>Built by the controller from the access token and the caller's current
 * permissions, so the service never reads security state itself.</p>
 *
 * @param tenantId          the caller's company; every lookup is scoped to it
 * @param userId            the caller
 * @param seesAllVacancies  holds {@code jobs.view.all}: sees every vacancy in
 *                          the company. Otherwise only the ones they work on —
 *                          created, recruit for, manage, or are on the hiring
 *                          team of — and every other one answers 404.
 */
public record VacancyViewer(UUID tenantId, UUID userId, boolean seesAllVacancies) {
}
