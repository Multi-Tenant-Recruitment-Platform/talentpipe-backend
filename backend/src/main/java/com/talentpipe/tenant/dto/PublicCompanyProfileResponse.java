package com.talentpipe.tenant.dto;

import java.util.List;

/**
 * Curated company profile for the public company page
 * ({@code GET /api/v1/public/companies/{subdomain}}).
 *
 * <p>Contains only display-safe fields — no internal billing, legal, or
 * operational data. The subdomain is included so the frontend can construct
 * canonical company URLs without an extra lookup.</p>
 */
public record PublicCompanyProfileResponse(
        String name,
        String subdomain,
        String logoUrl,
        String coverImageUrl,
        String tagline,
        String description,
        String industry,
        String size,
        Integer foundedYear,
        String website,
        String linkedinUrl,
        String twitterUrl,
        String instagramUrl,
        String facebookUrl,
        String city,
        String country,
        /** Industries besides {@code industry}; never null. */
        List<String> additionalIndustries
) {
}
