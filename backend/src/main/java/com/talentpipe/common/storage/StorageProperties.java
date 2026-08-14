package com.talentpipe.common.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration for the file storage layer.
 *
 * <p>Environment variables:</p>
 * <ul>
 *   <li>{@code STORAGE_PROVIDER} — {@code local} (default) or {@code s3}</li>
 *   <li>{@code UPLOAD_DIR} — base directory for local storage (default: {@code uploads})</li>
 *   <li>{@code AWS_S3_BUCKET} — S3 bucket name (required when provider is s3)</li>
 *   <li>{@code AWS_S3_REGION} — AWS region (default: {@code ap-south-1})</li>
 *   <li>{@code AWS_CDN_BASE_URL} — CloudFront base URL; if omitted, the direct
 *       S3 public URL is used</li>
 * </ul>
 */
@ConfigurationProperties(prefix = "talentpipe.storage")
public record StorageProperties(

        /** Active provider: {@code local} or {@code s3}. */
        String provider,

        /** Base directory for local storage. Resolved relative to the working directory. */
        String uploadDir,

        /** S3 bucket name. Required when provider = s3. */
        String s3Bucket,

        /** AWS region. */
        String s3Region,

        /** Optional CloudFront base URL. When present, CDN URLs are built from this. */
        String cdnBaseUrl,

        /** Maximum allowed logo file size in bytes (default 2 MB). */
        long maxLogoBytes,

        /** Maximum allowed cover image file size in bytes (default 4 MB). */
        long maxCoverBytes
) {
    private static final long DEFAULT_MAX_LOGO  = 2L * 1024 * 1024; // 2 MB
    private static final long DEFAULT_MAX_COVER = 4L * 1024 * 1024; // 4 MB

    /** Compact constructor supplies defaults for optional fields. */
    public StorageProperties {
        if (provider == null || provider.isBlank())    provider  = "local";
        if (uploadDir == null || uploadDir.isBlank())  uploadDir = "uploads";
        if (s3Region  == null || s3Region.isBlank())   s3Region  = "ap-south-1";
        if (maxLogoBytes  <= 0) maxLogoBytes  = DEFAULT_MAX_LOGO;
        if (maxCoverBytes <= 0) maxCoverBytes = DEFAULT_MAX_COVER;
    }
}
