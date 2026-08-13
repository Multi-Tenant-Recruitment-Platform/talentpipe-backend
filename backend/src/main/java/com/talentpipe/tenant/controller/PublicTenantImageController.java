package com.talentpipe.tenant.controller;

import com.talentpipe.tenant.dto.StoredImage;
import com.talentpipe.tenant.service.CompanyImageKind;
import com.talentpipe.tenant.service.TenantService;
import java.time.Duration;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Serves company brand images (PB-005).
 *
 * <p>Public, and deliberately so: an {@code <img>} tag cannot attach an
 * Authorization header, and these images appear on job adverts and the public
 * company page anyway. The exposure is narrow — the tenant id in the path
 * yields two image blobs and nothing else about the company. Guessing an id
 * gains an attacker a logo they could have seen on the careers page.</p>
 *
 * <p>Lives under {@code /api/v1/public/**}, which {@code SecurityConfig}
 * already permits without a token, so no new security rule is introduced.</p>
 */
@RestController
@RequestMapping("/api/v1/public/tenants")
public class PublicTenantImageController {

    private final TenantService tenantService;

    public PublicTenantImageController(TenantService tenantService) {
        this.tenantService = tenantService;
    }

    @GetMapping("/{tenantId}/logo")
    public ResponseEntity<byte[]> logo(@PathVariable UUID tenantId) {
        return image(tenantService.loadImage(tenantId, CompanyImageKind.LOGO));
    }

    @GetMapping("/{tenantId}/cover")
    public ResponseEntity<byte[]> cover(@PathVariable UUID tenantId) {
        return image(tenantService.loadImage(tenantId, CompanyImageKind.COVER));
    }

    /**
     * Cached for a year and marked immutable, which is safe because the URL
     * carries a version derived from the tenant's updatedAt: replacing an image
     * changes the URL, so a stale copy is never addressed again.
     */
    private ResponseEntity<byte[]> image(StoredImage stored) {
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(stored.contentType()))
                .cacheControl(CacheControl.maxAge(Duration.ofDays(365)).cachePublic().immutable())
                .body(stored.bytes());
    }
}
