package com.talentpipe.tenant.controller;

import com.talentpipe.security.UserPrincipal;
import com.talentpipe.tenant.dto.CompanyProfileResponse;
import com.talentpipe.tenant.dto.UpdateCompanyProfileRequest;
import com.talentpipe.tenant.service.CompanyImageKind;
import com.talentpipe.tenant.service.TenantService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * The signed-in company's own profile (PB-005).
 *
 * <p>Read and write are split on purpose. Reading is open to every member of
 * the workspace — it is the company they work for, and the same content is
 * destined to be candidate-visible on job adverts. Writing is COMPANY_ADMIN
 * only: an interviewer must not be able to rename the company.</p>
 *
 * <p>The tenant always comes from the caller's access token, exactly as
 * {@code TeamController} does. There is no path or body parameter that names a
 * company, so there is no request an admin could shape to reach another one.</p>
 */
@RestController
@RequestMapping("/api/v1/tenant")
public class TenantController {

    private final TenantService tenantService;

    public TenantController(TenantService tenantService) {
        this.tenantService = tenantService;
    }

    /** The caller's own company profile. Any authenticated workspace member. */
    @GetMapping
    public CompanyProfileResponse getProfile(@AuthenticationPrincipal UserPrincipal principal) {
        return tenantService.getProfile(principal.tenantId());
    }

    /** Replaces the editable fields. */
    @PatchMapping
    @PreAuthorize("hasRole('COMPANY_ADMIN')")
    public CompanyProfileResponse updateProfile(@AuthenticationPrincipal UserPrincipal principal,
                                                @Valid @RequestBody UpdateCompanyProfileRequest request) {
        return tenantService.updateProfile(principal.tenantId(), request);
    }

    // --- Brand images -------------------------------------------------------
    // Separate endpoints because they are multipart, and because a text save
    // must never be able to clear an image as a side effect.

    @PostMapping("/logo")
    @PreAuthorize("hasRole('COMPANY_ADMIN')")
    public CompanyProfileResponse uploadLogo(@AuthenticationPrincipal UserPrincipal principal,
                                             @RequestParam("file") MultipartFile file) {
        return tenantService.storeImage(principal.tenantId(), CompanyImageKind.LOGO, file);
    }

    @DeleteMapping("/logo")
    @PreAuthorize("hasRole('COMPANY_ADMIN')")
    public CompanyProfileResponse removeLogo(@AuthenticationPrincipal UserPrincipal principal) {
        return tenantService.removeImage(principal.tenantId(), CompanyImageKind.LOGO);
    }

    @PostMapping("/cover")
    @PreAuthorize("hasRole('COMPANY_ADMIN')")
    public CompanyProfileResponse uploadCover(@AuthenticationPrincipal UserPrincipal principal,
                                              @RequestParam("file") MultipartFile file) {
        return tenantService.storeImage(principal.tenantId(), CompanyImageKind.COVER, file);
    }

    @DeleteMapping("/cover")
    @PreAuthorize("hasRole('COMPANY_ADMIN')")
    public CompanyProfileResponse removeCover(@AuthenticationPrincipal UserPrincipal principal) {
        return tenantService.removeImage(principal.tenantId(), CompanyImageKind.COVER);
    }
}
