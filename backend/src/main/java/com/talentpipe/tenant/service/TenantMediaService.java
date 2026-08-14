package com.talentpipe.tenant.service;

import com.talentpipe.common.exception.UnsupportedMediaException;
import com.talentpipe.common.storage.FileStorageService;
import com.talentpipe.common.storage.StorageProperties;
import com.talentpipe.tenant.dto.CompanyProfileResponse;
import java.io.IOException;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * Handles image upload and deletion for tenant logos and cover images.
 *
 * <p>Responsibilities:</p>
 * <ol>
 *   <li>Validate MIME type against the allowed set.</li>
 *   <li>Validate file size against per-category limits.</li>
 *   <li>Delegate storage to {@link FileStorageService} (local or S3).</li>
 *   <li>Delete the old file when replacing an existing image.</li>
 *   <li>Update the tenant entity's URL field via {@link TenantService}.</li>
 *   <li>Return the full {@link CompanyProfileResponse} after update so the
 *       frontend can rehydrate state in a single round-trip.</li>
 * </ol>
 *
 * <p>Authorization (role + tenant scope) is enforced in the controller
 * before these methods are called. This service trusts that the {@code tenantId}
 * is already verified.</p>
 */
@Service
public class TenantMediaService {

    private static final Set<String> ALLOWED_MIME_TYPES = Set.of(
            "image/png",
            "image/jpeg",
            "image/svg+xml",
            "image/webp"
    );

    private final FileStorageService storageService;
    private final TenantService tenantService;
    private final StorageProperties storageProperties;

    public TenantMediaService(FileStorageService storageService,
                               TenantService tenantService,
                               StorageProperties storageProperties) {
        this.storageService = storageService;
        this.tenantService = tenantService;
        this.storageProperties = storageProperties;
    }

    /**
     * Uploads a new company logo for the given tenant.
     * If a logo already exists it is replaced (old file deleted from storage).
     *
     * @param tenantId owning tenant (from JWT)
     * @param file     the uploaded multipart file
     * @return the full company profile with the new {@code logoUrl}
     * @throws UnsupportedMediaException if the MIME type is not allowed
     * @throws IllegalArgumentException  if the file exceeds the logo size limit
     */
    @Transactional
    public CompanyProfileResponse uploadLogo(UUID tenantId, MultipartFile file) {
        validateMimeType(file);
        validateSize(file, storageProperties.maxLogoBytes(), "Logo");

        // Delete existing logo before storing the new one.
        tenantService.getProfile(tenantId).logoUrl();  // fetch to get current URL
        CompanyProfileResponse current = tenantService.getProfile(tenantId);
        if (current.logoUrl() != null) {
            storageService.delete(current.logoUrl());
        }

        String url = storeFile(tenantId, "logo", file);
        return tenantService.updateLogoUrl(tenantId, url);
    }

    /**
     * Uploads a new cover image for the given tenant.
     * If a cover image already exists it is replaced.
     *
     * @param tenantId owning tenant (from JWT)
     * @param file     the uploaded multipart file
     * @return the full company profile with the new {@code coverImageUrl}
     */
    @Transactional
    public CompanyProfileResponse uploadCover(UUID tenantId, MultipartFile file) {
        validateMimeType(file);
        validateSize(file, storageProperties.maxCoverBytes(), "Cover image");

        CompanyProfileResponse current = tenantService.getProfile(tenantId);
        if (current.coverImageUrl() != null) {
            storageService.delete(current.coverImageUrl());
        }

        String url = storeFile(tenantId, "cover", file);
        return tenantService.updateCoverImageUrl(tenantId, url);
    }

    /**
     * Removes the current logo. Idempotent — safe to call when no logo exists.
     *
     * @param tenantId owning tenant (from JWT)
     */
    @Transactional
    public void deleteLogo(UUID tenantId) {
        CompanyProfileResponse current = tenantService.getProfile(tenantId);
        if (current.logoUrl() != null) {
            storageService.delete(current.logoUrl());
        }
        tenantService.clearLogoUrl(tenantId);
    }

    /**
     * Removes the current cover image. Idempotent.
     *
     * @param tenantId owning tenant (from JWT)
     */
    @Transactional
    public void deleteCover(UUID tenantId) {
        CompanyProfileResponse current = tenantService.getProfile(tenantId);
        if (current.coverImageUrl() != null) {
            storageService.delete(current.coverImageUrl());
        }
        tenantService.clearCoverImageUrl(tenantId);
    }

    // ---------------------------------------------------------------- private

    private void validateMimeType(MultipartFile file) {
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_MIME_TYPES.contains(contentType)) {
            throw new UnsupportedMediaException(
                    "Unsupported file type: '" + contentType
                    + "'. Allowed types: image/png, image/jpeg, image/svg+xml, image/webp");
        }
    }

    private void validateSize(MultipartFile file, long maxBytes, String label) {
        if (file.getSize() > maxBytes) {
            long maxMb = maxBytes / (1024 * 1024);
            throw new IllegalArgumentException(
                    label + " file exceeds the " + maxMb + " MB limit");
        }
    }

    private String storeFile(UUID tenantId, String category, MultipartFile file) {
        try {
            return storageService.store(
                    tenantId,
                    category,
                    file.getOriginalFilename(),
                    file.getBytes(),
                    file.getContentType());
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read uploaded file", e);
        }
    }
}
