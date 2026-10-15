package com.talentpipe.job.dto;

import java.util.UUID;

/**
 * What the applications module needs to know about a vacancy it is about to
 * accept an application for — and nothing more. Handed out by
 * {@code VacancyApplicationGate}, so holding one means the vacancy was open at
 * the moment it was issued.
 *
 * <p>A DTO rather than the {@code JobVacancy} entity: entities never cross a
 * module boundary.</p>
 *
 * @param vacancyId the vacancy being applied to
 * @param tenantId  the company that owns it — the tenant the application belongs to
 * @param title     the advert's title, for confirmations and notifications
 */
public record VacancyApplicationTarget(UUID vacancyId, UUID tenantId, String title) {
}
