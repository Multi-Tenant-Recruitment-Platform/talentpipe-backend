package com.talentpipe.tenant.dto;

import java.util.UUID;

/**
 * The handful of company facts another module needs when it shows or reasons
 * about something a company owns — a vacancy on the public job board, say.
 * Returned in bulk by {@code TenantService.findCompanySummaries}.
 *
 * <p>Deliberately small: only fields that are already public on the company
 * page, plus the timezone, which callers need to work out what "today" is for
 * that company.</p>
 *
 * @param name      the company's display name
 * @param subdomain its unique URL-safe identifier
 * @param logoUrl   its logo, or {@code null} if it has not uploaded one
 * @param timezone  its IANA timezone id (e.g. {@code Asia/Colombo}), or
 *                  {@code null} if it has not set one
 */
public record CompanySummaryResponse(
        UUID id,
        String name,
        String subdomain,
        String logoUrl,
        String timezone
) {
}
