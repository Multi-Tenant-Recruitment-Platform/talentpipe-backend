package com.talentpipe.candidate.validator;

import com.talentpipe.common.exception.UnsupportedMediaException;
import com.talentpipe.common.storage.StorageProperties;
import com.talentpipe.common.util.AbstractFileValidator;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

/**
 * Validates candidate profile photo uploads.
 *
 * <p>Allowed formats: PNG, JPEG, WebP. Limit: 2 MB.</p>
 * <p>SVG is intentionally disallowed for profile avatars to eliminate Stored XSS vectors.</p>
 * <p>Enforces magic-byte header inspection to prevent renamed malicious payloads.</p>
 */
@Component
public class CandidatePhotoValidator extends AbstractFileValidator {

    private static final Set<String> ALLOWED_MIME_TYPES = Set.of(
            "image/png",
            "image/jpeg",
            "image/webp"
    );

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
            ".png",
            ".jpg",
            ".jpeg",
            ".webp"
    );

    private static final byte[] PNG_MAGIC_BYTES = {(byte) 0x89, 0x50, 0x4E, 0x47};
    private static final byte[] JPEG_MAGIC_BYTES = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
    private static final byte[] RIFF_MAGIC_BYTES = {0x52, 0x49, 0x46, 0x46}; // "RIFF"
    private static final byte[] WEBP_MAGIC_BYTES = {0x57, 0x45, 0x42, 0x50}; // "WEBP" at offset 8

    private final StorageProperties storageProperties;

    public CandidatePhotoValidator(StorageProperties storageProperties) {
        this.storageProperties = storageProperties;
    }

    @Override
    protected long getMaxSizeBytes() {
        return storageProperties.maxPhotoBytes();
    }

    @Override
    protected Set<String> getAllowedMimeTypes() {
        return ALLOWED_MIME_TYPES;
    }

    @Override
    protected Set<String> getAllowedExtensions() {
        return ALLOWED_EXTENSIONS;
    }

    @Override
    protected String getDescriptor() {
        return "Photo";
    }

    @Override
    protected void validateCustom(MultipartFile file) {
        String filename = file.getOriginalFilename();
        if (filename == null) {
            return;
        }

        String lower = filename.toLowerCase(Locale.ROOT);
        byte[] header = readHeaderBytes(file, 12);
        if (lower.endsWith(".png") && !matchesPrefix(header, PNG_MAGIC_BYTES)) {
            throw new UnsupportedMediaException("The uploaded file does not match a valid PNG image format.");
        }
        if ((lower.endsWith(".jpg") || lower.endsWith(".jpeg")) && !matchesPrefix(header, JPEG_MAGIC_BYTES)) {
            throw new UnsupportedMediaException("The uploaded file does not match a valid JPEG image format.");
        }
        if (lower.endsWith(".webp") && !isValidWebp(header)) {
            throw new UnsupportedMediaException("The uploaded file does not match a valid WebP image format.");
        }
    }

    private boolean isValidWebp(byte[] header) {
        return matchesPrefix(header, RIFF_MAGIC_BYTES) && matchesBytesAt(header, WEBP_MAGIC_BYTES, 8);
    }
}
