package com.talentpipe.common.storage;

import java.util.UUID;

/**
 * Abstraction over the file storage backend.
 *
 * <p>Two implementations are provided and selected at startup by the
 * {@code STORAGE_PROVIDER} environment variable (via {@link StorageProperties}):</p>
 * <ul>
 *   <li>{@link LocalFileStorageService} — writes to the local filesystem; suited
 *       for development and single-server deployments.</li>
 *   <li>{@link S3FileStorageService} — uploads to AWS S3 and returns a CDN-backed
 *       (CloudFront) or direct S3 public URL; suited for production.</li>
 * </ul>
 *
 * <p>Controllers and services depend on this interface only — switching the
 * underlying provider requires zero code changes anywhere except the env var.</p>
 */
public interface FileStorageService {

    /**
     * Stores the given file data and returns its public-accessible URL.
     *
     * @param tenantId  the owning tenant (used to namespace the storage path)
     * @param category  a short identifier for the file category, e.g. {@code logo}
     *                  or {@code cover}; used in the storage path
     * @param filename  original filename, used to derive the file extension
     * @param data      raw file bytes
     * @param mimeType  declared MIME type of the file
     * @return          a publicly accessible URL to the stored file
     */
    String store(UUID tenantId, String category, String filename, byte[] data, String mimeType);

    /**
     * Deletes the file identified by its stored URL or path. Implementations
     * must be idempotent — deleting a non-existent file must not throw.
     *
     * @param storedUrl the URL returned by a previous {@link #store} call
     */
    void delete(String storedUrl);
}
