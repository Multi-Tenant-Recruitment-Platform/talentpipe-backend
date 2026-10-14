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

    default String store(UUID tenantId, String category, String filename, byte[] data, String mimeType) {
        return store("tenant", tenantId, category, filename, data, mimeType);
    }

    /**
     * Stores file data under a specific namespace and entity id.
     *
     * @param namespace storage domain (e.g. "tenant", "candidate")
     * @param entityId  unique identifier of the owning entity
     * @param category  file category (e.g. "logo", "cover", "photos", "cvs")
     * @param filename  original filename, used to derive the file extension
     * @param data      raw file bytes
     * @param mimeType  declared MIME type of the file
     * @return          a publicly accessible URL or reference to the stored file
     */
    String store(String namespace, UUID entityId, String category, String filename, byte[] data, String mimeType);

    /**
     * Loads the raw file bytes for a stored URL or key.
     *
     * @param storedUrlOrKey the URL or storage key of the file
     * @return raw file bytes
     */
    byte[] load(String storedUrlOrKey);

    /**
     * Deletes the file identified by its stored URL or path. Implementations
     * must be idempotent — deleting a non-existent file must not throw.
     *
     * @param storedUrl the URL returned by a previous {@link #store} call
     */
    void delete(String storedUrl);
}
