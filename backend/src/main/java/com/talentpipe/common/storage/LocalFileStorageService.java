package com.talentpipe.common.storage;

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
        String ext = extractExtension(filename);
        Path dir = rootDir.resolve("tenant").resolve(tenantId.toString()).resolve(category);
        try {
            Files.createDirectories(dir);
            String storedName = UUID.randomUUID() + ext;
            Path target = dir.resolve(storedName);
            Files.write(target, data);
            log.debug("Stored file locally: {}", target);
            // Return relative URL path compatible with proxy and single-origin frontend.
            return "/api/v1/public/media/" + category + "/" + storedName;
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to store file", e);
        }
    }

    @Override
    public void delete(String storedUrl) {
        if (storedUrl == null || storedUrl.isBlank()) {
            return;
        }
        // Extract the filename from the URL and resolve the path.
        String[] parts = storedUrl.split("/");
        if (parts.length < 2) {
            return;
        }
        String category = parts[parts.length - 2];
        String filename  = parts[parts.length - 1];
        // Walk all tenant subdirectories to find the file (we don't have tenantId here).
        try {
            Path categoryPath = rootDir.resolve("tenant");
            if (!Files.exists(categoryPath)) {
                return;
            }
            try (Stream<Path> walker = Files.walk(categoryPath)) {
                walker.filter(p -> p.getFileName().toString().equals(filename)
                                && p.getParent().getFileName().toString().equals(category))
                        .forEach(p -> {
                            try {
                                Files.deleteIfExists(p);
                                log.debug("Deleted local file: {}", p);
                            } catch (IOException e) {
                                log.warn("Could not delete local file: {}", p, e);
                            }
                        });
            }
        } catch (IOException e) {
            log.warn("Error during local file deletion for URL: {}", storedUrl, e);
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
