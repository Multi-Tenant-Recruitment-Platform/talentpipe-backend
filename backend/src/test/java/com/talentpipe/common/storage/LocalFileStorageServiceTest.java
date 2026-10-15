package com.talentpipe.common.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Unit tests for {@link LocalFileStorageService}.
 * Uses a real temporary directory (@TempDir) — no mocks needed for filesystem ops.
 */
class LocalFileStorageServiceTest {

    @TempDir
    Path tempDir;

    private LocalFileStorageService storageService;
    private UUID tenantId;

    @BeforeEach
    void setUp() {
        StorageProperties props = new StorageProperties(
                "local", tempDir.toString(), null, "ap-south-1", null,
                2_097_152L, 4_194_304L, 2_097_152L, 5_242_880L);
        storageService = new LocalFileStorageService(props);
        tenantId = UUID.randomUUID();
    }

    // ---------------------------------------------------------------- store

    @Test
    void store_createsFileInExpectedDirectory() throws Exception {
        byte[] data = "hello world".getBytes();

        String url = storageService.store(tenantId, "logo", "logo.png", data, "image/png");
        assertThat(url).startsWith("/api/v1/public/media/logo/");

        // File must have been written to tempDir/tenant/{tenantId}/logo/
        Path expectedDir = tempDir.resolve("tenant").resolve(tenantId.toString()).resolve("logo");
        assertThat(expectedDir).isDirectory();
        assertThat(Files.list(expectedDir).count()).isEqualTo(1);
    }

    @Test
    void store_returnsNonNullUrl() {
        byte[] data = "image bytes".getBytes();
        String url = storageService.store(tenantId, "logo", "logo.png", data, "image/png");
        assertThat(url).startsWith("/api/v1/public/media/logo/").endsWith(".png");
    }

    @Test
    void store_withNullFilename_usesNoExtension() throws Exception {
        byte[] data = "data".getBytes();

        try {
            storageService.store(tenantId, "cover", null, data, "image/jpeg");
        } catch (IllegalStateException ignored) {
            // Outside servlet context — check file exists with no extension
        }

        Path dir = tempDir.resolve("tenant").resolve(tenantId.toString()).resolve("cover");
        assertThat(dir).isDirectory();
        long count = Files.list(dir).count();
        assertThat(count).isEqualTo(1);
        // File stored with no extension when filename is null
        String storedName = Files.list(dir).findFirst().get().getFileName().toString();
        assertThat(storedName).doesNotContain(".");
    }

    @Test
    void store_withFilenameWithoutExtension_usesNoExtension() throws Exception {
        byte[] data = "data".getBytes();

        try {
            storageService.store(tenantId, "logo", "logo-no-ext", data, "image/png");
        } catch (IllegalStateException ignored) {
            // Outside servlet context
        }

        Path dir = tempDir.resolve("tenant").resolve(tenantId.toString()).resolve("logo");
        String storedName = Files.list(dir).findFirst().get().getFileName().toString();
        assertThat(storedName).doesNotContain(".");
    }

    @Test
    void store_preservesExtensionInLowercase() throws Exception {
        byte[] data = "data".getBytes();

        try {
            storageService.store(tenantId, "logo", "LOGO.PNG", data, "image/png");
        } catch (IllegalStateException ignored) {
            // Outside servlet context
        }

        Path dir = tempDir.resolve("tenant").resolve(tenantId.toString()).resolve("logo");
        String storedName = Files.list(dir).findFirst().get().getFileName().toString();
        assertThat(storedName).endsWith(".png"); // extension lowercased
    }

    // ---------------------------------------------------------------- delete

    @Test
    void delete_existingFile_removesIt() throws Exception {
        // First store a file
        Path dir = tempDir.resolve("tenant").resolve(tenantId.toString()).resolve("logo");
        Files.createDirectories(dir);
        Path file = dir.resolve("abc123.png");
        Files.write(file, "image".getBytes());

        // Construct the URL that LocalFileStorageService would produce
        String fakeUrl = "http://localhost:8080/api/v1/public/media/logo/abc123.png";

        storageService.delete(fakeUrl);

        assertThat(file).doesNotExist();
    }

    @Test
    void delete_nonExistingFile_doesNotThrow() {
        String fakeUrl = "http://localhost:8080/api/v1/public/media/logo/nonexistent.png";

        // Should complete without exception even though no tenant dirs exist yet
        storageService.delete(fakeUrl);
    }

    @Test
    void delete_nullUrl_doesNothing() {
        storageService.delete(null);
        // No exception — idempotent
    }

    @Test
    void delete_blankUrl_doesNothing() {
        storageService.delete("   ");
        // No exception — idempotent
    }

    @Test
    void delete_urlWithTooFewParts_doesNothing() {
        storageService.delete("http://localhost/onlyone");
        // parts.length < 2 guard — should not throw
    }

    @Test
    void delete_whenTenantDirAbsent_doesNotThrow() {
        // tempDir/tenant does NOT exist — should return early without exception
        String url = "http://localhost:8080/api/v1/public/media/logo/missing.png";
        storageService.delete(url);
    }

    @Test
    void delete_onlyDeletesMatchingCategoryAndFilename() throws Exception {
        // Store two files: one in logo/, one in cover/
        Path logoDir  = tempDir.resolve("tenant").resolve(tenantId.toString()).resolve("logo");
        Path coverDir = tempDir.resolve("tenant").resolve(tenantId.toString()).resolve("cover");
        Files.createDirectories(logoDir);
        Files.createDirectories(coverDir);

        Path logoFile  = logoDir.resolve("target.png");
        Path coverFile = coverDir.resolve("target.png");  // same filename, different category
        Files.write(logoFile,  "logo".getBytes());
        Files.write(coverFile, "cover".getBytes());

        // Delete only the logo
        storageService.delete("http://localhost/api/v1/public/media/logo/target.png");

        assertThat(logoFile).doesNotExist();
        assertThat(coverFile).exists();  // cover file must be untouched
    }

    // ---------------------------------------------------------------- constructor

    @Test
    void constructor_createsUploadDirectoryIfAbsent() {
        Path newDir = tempDir.resolve("auto-created");
        StorageProperties props = new StorageProperties(
                "local", newDir.toString(), null, "ap-south-1", null,
                2_097_152L, 4_194_304L, 2_097_152L, 5_242_880L);

        new LocalFileStorageService(props);

        assertThat(newDir).isDirectory();
    }

    // ---------------------------------------------------------------- candidate storage & load

    @Test
    void store_candidatePhoto_returnsCandidateMediaUrl() {
        UUID candidateId = UUID.randomUUID();
        byte[] data = "photo-bytes".getBytes();

        String url = storageService.store("candidate", candidateId, "photos", "avatar.jpg", data, "image/jpeg");

        assertThat(url).startsWith("/api/v1/public/media/candidate/" + candidateId + "/photos/").endsWith(".jpg");
        byte[] loaded = storageService.load(url);
        assertThat(loaded).isEqualTo(data);
    }

    @Test
    void store_candidateCv_returnsRelativeStorageKey() {
        UUID candidateId = UUID.randomUUID();
        byte[] data = "cv-pdf-content".getBytes();

        String key = storageService.store("candidate", candidateId, "cvs", "resume.pdf", data, "application/pdf");

        assertThat(key).startsWith("candidate/" + candidateId + "/cvs/").endsWith(".pdf");
        byte[] loaded = storageService.load(key);
        assertThat(loaded).isEqualTo(data);
    }

    @Test
    void load_tenantMedia_readsBytes() {
        byte[] data = "logo-bytes".getBytes();
        String url = storageService.store(tenantId, "logo", "logo.png", data, "image/png");

        byte[] loaded = storageService.load(url);
        assertThat(loaded).isEqualTo(data);
    }

    @Test
    void load_nullOrBlankThrowsIllegalArgumentException() {
        assertThatThrownBy(() -> storageService.load(null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> storageService.load("   "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void load_nonExistentFileThrowsResourceNotFoundException() {
        assertThatThrownBy(() -> storageService.load("candidate/missing/cvs/nonexistent.pdf"))
                .isInstanceOf(com.talentpipe.common.exception.ResourceNotFoundException.class);
    }

    @Test
    void delete_candidatePhoto_removesFile() {
        UUID candidateId = UUID.randomUUID();
        String url = storageService.store("candidate", candidateId, "photos", "avatar.jpg", "avatar".getBytes(), "image/jpeg");

        storageService.delete(url);

        assertThatThrownBy(() -> storageService.load(url))
                .isInstanceOf(com.talentpipe.common.exception.ResourceNotFoundException.class);
    }

    @Test
    void delete_candidateCvByRelativeKey_removesFile() {
        UUID candidateId = UUID.randomUUID();
        String key = storageService.store("candidate", candidateId, "cvs", "resume.pdf", "pdf".getBytes(), "application/pdf");

        storageService.delete(key);

        assertThatThrownBy(() -> storageService.load(key))
                .isInstanceOf(com.talentpipe.common.exception.ResourceNotFoundException.class);
    }
}
