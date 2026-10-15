package com.talentpipe.common.storage;

import com.talentpipe.common.exception.ResourceNotFoundException;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Local filesystem implementation of {@link FileStorageService}.
 *
 * <p>Files are written to {@code {uploadDir}/tenant/{tenantId}/{category}/{uuid}.{ext}}.
 * The UUID filename prevents name collisions and URL guessing. The returned
 * URL is a root-relative path, which the frontend resolves against whatever
 * origin it is served from — so the same stored value works behind a dev proxy
 * and a single-origin deployment alike, and survives a host change.
 * In production, switch to {@link S3FileStorageService} via
 * {@code STORAGE_PROVIDER=s3}.</p>
 *
 * <p><b>Not activated in production.</b> Selected only when
 * {@code talentpipe.storage.provider=local} (the default).</p>
 */
public class LocalFileStorageService implements FileStorageService {

    private static final Logger log = LoggerFactory.getLogger(LocalFileStorageService.class);
    private static final String NAMESPACE_TENANT = "tenant";
    private static final String PUBLIC_MEDIA_PREFIX = "/api/v1/public/media/";
    private static final String CANDIDATE_MEDIA_PREFIX = "/api/v1/public/media/candidate/";

    private final Path rootDir;

    public LocalFileStorageService(StorageProperties properties) {
        this.rootDir = Paths.get(properties.uploadDir()).toAbsolutePath().normalize();
        try {
            Files.createDirectories(rootDir);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not create storage directory: " + rootDir, e);
        }
    }

    @Override
    public String store(UUID tenantId, String category, String filename,
                        byte[] data, String mimeType) {
        return store(NAMESPACE_TENANT, tenantId, category, filename, data, mimeType);
    }

    @Override
    public String store(String namespace, UUID entityId, String category,
                        String filename, byte[] data, String mimeType) {
        String ext = extractExtension(filename);
        Path dir = rootDir.resolve(namespace).resolve(entityId.toString()).resolve(category);
        try {
            Files.createDirectories(dir);
            String storedName = UUID.randomUUID() + ext;
            Path target = dir.resolve(storedName);
            Files.write(target, data);
            log.debug("Stored file locally: {}", target);

            if (NAMESPACE_TENANT.equals(namespace)) {
                return PUBLIC_MEDIA_PREFIX + category + "/" + storedName;
            }
            if ("candidate".equals(namespace) && "photos".equals(category)) {
                return CANDIDATE_MEDIA_PREFIX + entityId + "/photos/" + storedName;
            }
            // For CVs or other protected documents, return relative key
            return namespace + "/" + entityId + "/" + category + "/" + storedName;
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to store file", e);
        }
    }

    @Override
    public byte[] load(String storedUrlOrKey) {
        if (storedUrlOrKey == null || storedUrlOrKey.isBlank()) {
            throw new IllegalArgumentException("Storage path or key cannot be blank");
        }
        Path targetPath;
        if (storedUrlOrKey.startsWith(CANDIDATE_MEDIA_PREFIX)) {
            String relative = storedUrlOrKey.substring(PUBLIC_MEDIA_PREFIX.length());
            targetPath = rootDir.resolve(relative);
        } else if (storedUrlOrKey.startsWith(PUBLIC_MEDIA_PREFIX)) {
            String[] parts = storedUrlOrKey.split("/");
            String category = parts[parts.length - 2];
            String filename = parts[parts.length - 1];
            targetPath = findLocalFile(rootDir.resolve(NAMESPACE_TENANT), category, filename);
        } else {
            targetPath = rootDir.resolve(storedUrlOrKey);
        }

        if (targetPath == null || !Files.exists(targetPath) || !Files.isRegularFile(targetPath)) {
            throw new ResourceNotFoundException("File not found at storage location: " + storedUrlOrKey);
        }
        try {
            return Files.readAllBytes(targetPath);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read file from storage: " + targetPath, e);
        }
    }

    @Override
    public void delete(String storedUrl) {
        if (storedUrl == null || storedUrl.isBlank()) {
            return;
        }
        String path = storedUrl;
        if (path.contains("://")) {
            int pathStart = path.indexOf('/', path.indexOf("://") + 3);
            path = pathStart >= 0 ? path.substring(pathStart) : "";
        }
        if (path.startsWith(CANDIDATE_MEDIA_PREFIX)) {
            String relative = path.substring(PUBLIC_MEDIA_PREFIX.length());
            deleteQuietly(rootDir.resolve(relative));
            return;
        }
        if (!path.startsWith("/") && !storedUrl.contains("://")) {
            deleteQuietly(rootDir.resolve(storedUrl));
            return;
        }

        // Extract category and filename for tenant media legacy deletion
        String[] parts = path.split("/");
        if (parts.length < 2) {
            return;
        }
        String category = parts[parts.length - 2];
        String filename = parts[parts.length - 1];

        Path tenantRootDir = rootDir.resolve(NAMESPACE_TENANT);
        if (!Files.exists(tenantRootDir)) {
            return;
        }
        try (Stream<Path> walker = Files.walk(tenantRootDir)) {
            walker.filter(p -> p.getFileName().toString().equals(filename)
                            && p.getParent().getFileName().toString().equals(category))
                    .forEach(this::deleteQuietly);
        } catch (IOException e) {
            log.warn("Error during local file deletion for URL: {}", storedUrl, e);
        }
    }

    private Path findLocalFile(Path baseDir, String category, String filename) {
        if (!Files.exists(baseDir)) {
            return null;
        }
        try (Stream<Path> walker = Files.walk(baseDir)) {
            return walker.filter(p -> p.getFileName().toString().equals(filename)
                            && p.getParent().getFileName().toString().equals(category))
                    .findFirst()
                    .orElse(null);
        } catch (IOException e) {
            return null;
        }
    }

    private void deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
            log.debug("Deleted local file: {}", path);
        } catch (IOException e) {
            log.warn("Could not delete local file: {}", path, e);
        }
    }

    private String extractExtension(String filename) {
        if (filename == null) {
            return "";
        }
        int dot = filename.lastIndexOf('.');
        return dot >= 0 ? filename.substring(dot).toLowerCase() : "";
    }
}
