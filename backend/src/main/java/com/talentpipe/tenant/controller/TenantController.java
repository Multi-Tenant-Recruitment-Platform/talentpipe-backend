package com.talentpipe.tenant.controller;

import com.talentpipe.tenant.dto.CompanyProfileResponse;
import com.talentpipe.tenant.dto.UpdateCompanyProfileRequest;
import com.talentpipe.tenant.service.TenantMediaService;
import com.talentpipe.tenant.service.TenantService;
import com.talentpipe.security.UserPrincipal;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Company profile endpoints for the Dashboard Settings page.
 *
 * <p>Authorization is enforced twice, per platform convention (DECISIONS.md):</p>
 * <ol>
 *   <li>{@code @PreAuthorize} on each method rejects wrong roles with 403
 *       before any work happens.</li>
 *   <li>{@link TenantService} and {@link TenantMediaService} independently
 *       re-verify tenant ownership via {@code ResourceNotFoundException} (404),
 *       so a future internal caller bypassing the controller cannot bypass
 *       tenant scoping.</li>
 * </ol>
 *
 * <p>The tenant identity comes exclusively from the JWT claim
 * ({@code UserPrincipal.tenantId()}) — never from the request body, path, or
 * query parameter.</p>
 */
@RestController
@RequestMapping("/api/v1/tenant")
public class TenantController {

    private final TenantService tenantService;
    private final TenantMediaService tenantMediaService;

    public TenantController(TenantService tenantService, TenantMediaService tenantMediaService) {
        this.tenantService = tenantService;
        this.tenantMediaService = tenantMediaService;
    }

    // ------------------------------------------------------------------ GET

    /**
     * Returns the full company profile for the authenticated user's tenant.
     * Accessible by COMPANY_ADMIN, HR_MANAGER, and INTERVIEWER — all roles
     * that use the dashboard header showing company branding.
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN', 'HR_MANAGER', 'INTERVIEWER')")
    public CompanyProfileResponse getProfile(@AuthenticationPrincipal UserPrincipal principal) {
        return tenantService.getProfile(principal.tenantId());
    }

    // ---------------------------------------------------------------- PATCH

    /**
     * Updates editable profile fields. COMPANY_ADMIN only.
     * Image fields (logoUrl, coverImageUrl) are NOT updated here — use the
     * dedicated image endpoints below.
     */
    @PatchMapping
    @PreAuthorize("hasRole('COMPANY_ADMIN')")
    public CompanyProfileResponse updateProfile(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody UpdateCompanyProfileRequest request) {
        return tenantService.updateProfile(principal.tenantId(), request);
    }

    // -------------------------------------------------------------- images

    /**
     * Uploads (or replaces) the company logo. COMPANY_ADMIN only.
     * Accepted types: image/png, image/jpeg, image/svg+xml, image/webp. Max 2 MB.
     * Returns the full profile with the new logoUrl so the frontend can
     * rehydrate state in a single round-trip.
     */
    @PostMapping(value = "/logo", consumes = "multipart/form-data")
    @PreAuthorize("hasRole('COMPANY_ADMIN')")
    public CompanyProfileResponse uploadLogo(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestPart("file") MultipartFile file) {
        return tenantMediaService.uploadLogo(principal.tenantId(), file);
    }

    /**
     * Uploads (or replaces) the cover image. COMPANY_ADMIN only. Max 4 MB.
     */
    @PostMapping(value = "/cover", consumes = "multipart/form-data")
    @PreAuthorize("hasRole('COMPANY_ADMIN')")
    public CompanyProfileResponse uploadCover(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestPart("file") MultipartFile file) {
        return tenantMediaService.uploadCover(principal.tenantId(), file);
    }

    /**
     * Removes the current logo. Idempotent — safe to call when no logo exists.
     */
    @DeleteMapping("/logo")
    @PreAuthorize("hasRole('COMPANY_ADMIN')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteLogo(@AuthenticationPrincipal UserPrincipal principal) {
        tenantMediaService.deleteLogo(principal.tenantId());
    }

    /**
     * Removes the current cover image. Idempotent.
     */
    @DeleteMapping("/cover")
    @PreAuthorize("hasRole('COMPANY_ADMIN')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteCover(@AuthenticationPrincipal UserPrincipal principal) {
        tenantMediaService.deleteCover(principal.tenantId());
    }
}
