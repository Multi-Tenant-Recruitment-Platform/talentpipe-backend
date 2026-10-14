package com.talentpipe.candidate.validator;

import com.talentpipe.common.exception.UnsupportedMediaException;
import com.talentpipe.common.storage.StorageProperties;
import com.talentpipe.common.util.AbstractFileValidator;
import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

/**
 * Validates candidate CV / resume uploads.
 *
 * <p>Allowed formats: PDF, DOCX. Limit: 5 MB.</p>
 * <p>Enforces magic-byte header inspection to prevent renamed malicious payloads.</p>
 */
@Component
public class CandidateCvValidator extends AbstractFileValidator {

    private static final Set<String> ALLOWED_MIME_TYPES = Set.of(
            "application/pdf",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
    );

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
            ".pdf",
            ".docx"
    );

    private static final byte[] PDF_MAGIC_BYTES = new byte[]{0x25, 0x50, 0x44, 0x46}; // "%PDF"
    private static final byte[] ZIP_MAGIC_BYTES = new byte[]{0x50, 0x4B};             // "PK" for DOCX

    private final StorageProperties storageProperties;

    public CandidateCvValidator(StorageProperties storageProperties) {
        this.storageProperties = storageProperties;
    }

    @Override
    protected long getMaxSizeBytes() {
        return storageProperties.maxCvBytes();
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
        return "CV";
    }

    @Override
    protected void validateCustom(MultipartFile file) {
        String filename = file.getOriginalFilename();
        if (filename == null) {
            return;
        }

        String lower = filename.toLowerCase(Locale.ROOT);
        try (InputStream in = file.getInputStream()) {
            byte[] header = in.readNBytes(8);
            if (lower.endsWith(".pdf") && !matchesPrefix(header, PDF_MAGIC_BYTES)) {
                throw new UnsupportedMediaException("The uploaded file does not match a valid PDF format.");
            }
            if (lower.endsWith(".docx") && !matchesPrefix(header, ZIP_MAGIC_BYTES)) {
                throw new UnsupportedMediaException("The uploaded file does not match a valid DOCX format.");
            }
        } catch (IOException e) {
            throw new IllegalArgumentException("Could not read CV file header.", e);
        }
    }

    private boolean matchesPrefix(byte[] data, byte[] prefix) {
        if (data == null || data.length < prefix.length) {
            return false;
        }
        for (int i = 0; i < prefix.length; i++) {
            if (data[i] != prefix[i]) {
                return false;
            }
        }
        return true;
    }
}
