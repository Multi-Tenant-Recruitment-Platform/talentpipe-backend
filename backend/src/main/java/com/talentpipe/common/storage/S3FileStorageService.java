package com.talentpipe.common.storage;

import com.talentpipe.common.exception.ResourceNotFoundException;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

/**
 * AWS S3 implementation of {@link FileStorageService}.
 *
 * <p>Files are stored at key {@code tenant/{tenantId}/{category}/{uuid}.{ext}}
 * and returned as CDN-backed URLs if {@code talentpipe.storage.cdn-base-url}
 * is set, otherwise as direct S3 public URLs. Objects are uploaded with
 * {@code public-read} ACL so the returned URL is immediately accessible by
 * browser {@code <img>} tags without authentication.</p>
 *
 * <p><b>Activated in production</b> when {@code STORAGE_PROVIDER=s3}.
 * Requires:</p>
 * <ul>
 *   <li>{@code AWS_S3_BUCKET} — target bucket name</li>
 *   <li>{@code AWS_S3_REGION} — AWS region (default: {@code ap-south-1})</li>
 *   <li>{@code AWS_CDN_BASE_URL} — optional CloudFront base URL</li>
 *   <li>IAM credentials via the standard AWS credential chain
 *       (environment variables, instance profile, etc.)</li>
 * </ul>
 */
public class S3FileStorageService implements FileStorageService {

    private static final Logger log = LoggerFactory.getLogger(S3FileStorageService.class);

    private final S3Client s3;
    private final StorageProperties properties;

    public S3FileStorageService(S3Client s3, StorageProperties properties) {
        this.s3 = s3;
        this.properties = properties;
    }

    @Override
    public String store(UUID tenantId, String category, String filename,
                        byte[] data, String mimeType) {
        return store("tenant", tenantId, category, filename, data, mimeType);
    }

    @Override
    public String store(String namespace, UUID entityId, String category,
                        String filename, byte[] data, String mimeType) {
        String ext = extractExtension(filename);
        String key = namespace + "/" + entityId + "/" + category + "/" + UUID.randomUUID() + ext;

        PutObjectRequest putRequest = PutObjectRequest.builder()
                .bucket(properties.s3Bucket())
                .key(key)
                .contentType(mimeType)
                .contentLength((long) data.length)
                .build();

        s3.putObject(putRequest, RequestBody.fromBytes(data));
        log.debug("Stored S3 object: s3://{}/{}", properties.s3Bucket(), key);

        if ("cvs".equals(category)) {
            // CVs stay as private keys for controlled access/download
            return key;
        }

        return buildPublicUrl(key);
    }

    @Override
    public byte[] load(String storedUrlOrKey) {
        if (storedUrlOrKey == null || storedUrlOrKey.isBlank()) {
            throw new IllegalArgumentException("Storage path or key cannot be blank");
        }
        String key = extractKeyFromUrl(storedUrlOrKey);
        if (key == null) {
            key = storedUrlOrKey;
        }
        try {
            GetObjectRequest getRequest = GetObjectRequest.builder()
                    .bucket(properties.s3Bucket())
                    .key(key)
                    .build();
            ResponseBytes<GetObjectResponse> response = s3.getObjectAsBytes(getRequest);
            return response.asByteArray();
        } catch (NoSuchKeyException e) {
            throw new ResourceNotFoundException("File not found in S3: " + key);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to load S3 object: " + key, e);
        }
    }

    @Override
    public void delete(String storedUrl) {
        if (storedUrl == null || storedUrl.isBlank()) {
            return;
        }
        String key = extractKeyFromUrl(storedUrl);
        if (key == null) {
            key = storedUrl;
        }
        try {
            s3.deleteObject(DeleteObjectRequest.builder()
                    .bucket(properties.s3Bucket())
                    .key(key)
                    .build());
            log.debug("Deleted S3 object: s3://{}/{}", properties.s3Bucket(), key);
        } catch (NoSuchKeyException e) {
            // Idempotent — object already gone, no action required.
            log.debug("S3 object already absent: s3://{}/{}", properties.s3Bucket(), key);
        }
    }

    // ---------------------------------------------------------------- private

    private String buildPublicUrl(String key) {
        String cdn = properties.cdnBaseUrl();
        if (cdn != null && !cdn.isBlank()) {
            // Strip trailing slash from CDN base, add key.
            return cdn.stripTrailing().replaceAll("/$", "") + "/" + key;
        }
        // Fall back to direct S3 URL.
        return "https://" + properties.s3Bucket() + ".s3."
                + properties.s3Region() + ".amazonaws.com/" + key;
    }

    private String extractKeyFromUrl(String url) {
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            return url;
        }
        int tenantIdx = url.indexOf("/tenant/");
        if (tenantIdx >= 0) {
            return url.substring(tenantIdx + 1);
        }
        int candidateIdx = url.indexOf("/candidate/");
        if (candidateIdx >= 0) {
            return url.substring(candidateIdx + 1);
        }
        return null;
    }

    private String extractExtension(String filename) {
        if (filename == null) {
            return "";
        }
        int dot = filename.lastIndexOf('.');
        return dot >= 0 ? filename.substring(dot).toLowerCase() : "";
    }
}
