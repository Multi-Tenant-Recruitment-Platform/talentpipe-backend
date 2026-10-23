package com.talentpipe.tenant.dto;

/**
 * Changes to a company's feature switches. Unlike the profile update, a
 * switch left out (or {@code null}) keeps its current value, so a screen
 * that shows one switch can save just that one.
 *
 * @param aiScreeningDefault       applies to vacancies created from now on
 * @param candidateMatchingDefault applies to vacancies created from now on
 */
public record UpdateTenantSettingsRequest(
        Boolean blindScreeningEnabled,
        Boolean aiScreeningDefault,
        Boolean candidateMatchingDefault
) {
}
