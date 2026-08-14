package com.talentpipe.tenant.controller;

import com.talentpipe.common.exception.ResourceNotFoundException;
import com.talentpipe.tenant.dto.PublicCompanyProfileResponse;
import com.talentpipe.tenant.service.TenantService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public (no-auth) company information endpoint.
 *
 * <p>Returns a curated subset of the company profile — display fields only.
 * Billing, legal, internal contact, and taxonomy data are excluded. The
 * response is safe to cache at a CDN layer since it requires no user context.</p>
 *
 * <p>Returns 404 for unknown subdomains (indistinguishable from inactive /
 * suspended tenants by design — existence must not leak).</p>
 */
@RestController
@RequestMapping("/api/v1/public/companies")
public class PublicCompanyController {

    private final TenantService tenantService;

    public PublicCompanyController(TenantService tenantService) {
        this.tenantService = tenantService;
    }

    /**
     * Returns the curated public profile for the given company subdomain.
     *
     * @param subdomain the company's unique URL-safe identifier
     * @return curated public profile
     * @throws ResourceNotFoundException (404) when the subdomain is not found
     */
    @GetMapping("/{subdomain}")
    public PublicCompanyProfileResponse getPublicProfile(@PathVariable String subdomain) {
        return tenantService.getPublicProfile(subdomain)
                .orElseThrow(() -> new ResourceNotFoundException("Company not found"));
    }
}
