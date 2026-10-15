package com.talentpipe.common.util;

import com.talentpipe.common.exception.UnsupportedMediaException;
import java.util.Locale;
import java.util.Set;
import org.springframework.web.multipart.MultipartFile;

/**
 * Base template for validating uploaded files against size, MIME, and extension rules.
 *
 * <p>Implements the Template Method pattern to enforce a unified validation pipeline
 * across all uploaded file types while allowing subclasses to declare specific limits.</p>
 */
public abstract class AbstractFileValidator {

    /**
     * Executes the standardized validation pipeline.
     *
     * @param file uploaded file to validate
     * @throws IllegalArgumentException if the file is empty or exceeds the size limit
     * @throws UnsupportedMediaException if the file type or extension is unsupported
     */
    public final void validate(MultipartFile file) {
        ensureNotNullOrEmpty(file);
        ensureWithinSizeLimit(file);
        ensureAllowedMimeType(file);
        ensureAllowedExtension(file);
        validateCustom(file);
    }

    protected abstract long getMaxSizeBytes();

    protected abstract Set<String> getAllowedMimeTypes();

    protected abstract Set<String> getAllowedExtensions();

    protected abstract String getDescriptor();

    /** Hook for subclass-specific validation, e.g. magic byte checking. */
    protected void validateCustom(MultipartFile file) {
        // default no-op
    }

    private void ensureNotNullOrEmpty(MultipartFile file) {
        if (file == null || file.isEmpty() || file.getSize() == 0) {
            throw new IllegalArgumentException(getDescriptor() + " file is empty. Please select a valid file.");
        }
    }

    private void ensureWithinSizeLimit(MultipartFile file) {
        long maxSize = getMaxSizeBytes();
        if (file.getSize() > maxSize) {
            long maxMb = maxSize / (1024 * 1024);
            throw new IllegalArgumentException(
                    getDescriptor() + " file exceeds the maximum limit of " + maxMb + " MB.");
        }
    }

    private void ensureAllowedMimeType(MultipartFile file) {
        String contentType = file.getContentType();
        Set<String> allowedTypes = getAllowedMimeTypes();
        // Allow empty MIME type if the extension matches (e.g. Windows DOCX without Office installed)
        if (contentType != null && !contentType.isBlank() && !allowedTypes.contains(contentType.toLowerCase(Locale.ROOT))) {
            throw new UnsupportedMediaException(
                    "Unsupported " + getDescriptor().toLowerCase(Locale.ROOT)
                            + " format: '" + contentType + "'. Allowed formats: " + String.join(", ", allowedTypes));
        }
    }

    private void ensureAllowedExtension(MultipartFile file) {
        String filename = file.getOriginalFilename();
        if (filename == null || !hasAllowedExtension(filename)) {
            throw new UnsupportedMediaException(
                    "Unsupported file extension for " + getDescriptor().toLowerCase(Locale.ROOT)
                            + ". Allowed extensions: " + String.join(", ", getAllowedExtensions()));
        }
    }

    private boolean hasAllowedExtension(String filename) {
        String lower = filename.toLowerCase(Locale.ROOT);
        return getAllowedExtensions().stream().anyMatch(lower::endsWith);
    }

    /**
     * Reads up to {@code count} bytes from the start of the file stream.
     */
    protected byte[] readHeaderBytes(MultipartFile file, int count) {
        try (java.io.InputStream in = file.getInputStream()) {
            return in.readNBytes(count);
        } catch (java.io.IOException e) {
            throw new IllegalArgumentException("Could not read " + getDescriptor() + " file header.", e);
        }
    }

    /**
     * Returns {@code true} when {@code data} starts with every byte in {@code prefix}.
     *
     * @param data   the header bytes read from the file
     * @param prefix the magic-byte sequence to check
     */
    protected static boolean matchesPrefix(byte[] data, byte[] prefix) {
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

    /**
     * Returns {@code true} when {@code data} contains {@code pattern} starting at {@code offset}.
     *
     * @param data    the header bytes read from the file
     * @param pattern the byte sequence to match
     * @param offset  byte offset within {@code data} at which to start matching
     */
    protected static boolean matchesBytesAt(byte[] data, byte[] pattern, int offset) {
        if (data == null || data.length < offset + pattern.length) {
            return false;
        }
        for (int i = 0; i < pattern.length; i++) {
            if (data[offset + i] != pattern[i]) {
                return false;
            }
        }
        return true;
    }
}
