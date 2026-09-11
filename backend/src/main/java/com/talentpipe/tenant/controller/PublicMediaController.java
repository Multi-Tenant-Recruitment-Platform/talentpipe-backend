package com.talentpipe.tenant.controller;

import com.talentpipe.common.exception.ResourceNotFoundException;
import com.talentpipe.common.storage.StorageProperties;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller for serving locally stored media files (logos and cover images).
 *
 * <p>Serves files stored by {@code LocalFileStorageService} under
 * {@code {uploadDir}/tenant/{tenantId}/{category}/{filename}}.</p>
 */
@RestController
@RequestMapping("/api/v1/public/media")
@ConditionalOnProperty(name = "talentpipe.storage.provider", havingValue = "local", matchIfMissing = true)
public class PublicMediaController {

    private final Path rootDir;

    public PublicMediaController(StorageProperties properties) {
        this.rootDir = Paths.get(properties.uploadDir()).toAbsolutePath().normalize();
    }

    @GetMapping("/{category}/{filename:.+}")
    public ResponseEntity<Resource> getMediaFile(
            @PathVariable String category,
            @PathVariable String filename) {
        Path categoryPath = rootDir.resolve("tenant");
        if (!Files.exists(categoryPath)) {
            throw new ResourceNotFoundException("Media directory not found");
        }

        try (Stream<Path> walker = Files.walk(categoryPath)) {
            Path matchedFile = walker
                    .filter(p -> Files.isRegularFile(p)
                            && p.getFileName().toString().equals(filename)
                            && p.getParent().getFileName().toString().equals(category))
                    .findFirst()
                    .orElseThrow(() -> new ResourceNotFoundException("Media file not found"));

            Resource resource = new FileSystemResource(matchedFile);
            String contentType = Files.probeContentType(matchedFile);
            if (contentType == null) {
                contentType = MediaType.APPLICATION_OCTET_STREAM_VALUE;
            }

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_TYPE, contentType)
                    .body(resource);
        } catch (IOException e) {
            throw new ResourceNotFoundException("Could not read media file");
        }
    }
}
