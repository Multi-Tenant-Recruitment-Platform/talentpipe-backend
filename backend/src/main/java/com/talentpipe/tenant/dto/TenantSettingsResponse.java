package com.talentpipe.tenant.dto;

import java.time.Instant;

/**
 * A company's feature switches ({@code GET /api/v1/tenant/settings}).
 *
 * @param blindScreeningEnabled    reviewers see applications without who sent them
 * @param aiScreeningDefault       whether a new vacancy starts with AI screening on
 * @param candidateMatchingDefault whether a new vacancy starts with talent-pool matching on
 * @param updatedAt                when the company record last changed
 */
public record TenantSettingsResponse(
        boolean blindScreeningEnabled,
        boolean aiScreeningDefault,
        boolean candidateMatchingDefault,
        Instant updatedAt
) {
}
