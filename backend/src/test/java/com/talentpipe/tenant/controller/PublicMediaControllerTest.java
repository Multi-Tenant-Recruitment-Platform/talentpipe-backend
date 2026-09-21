package com.talentpipe.tenant.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mockStatic;

import com.talentpipe.common.exception.ResourceNotFoundException;
import com.talentpipe.common.storage.StorageProperties;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

/**
 * Unit tests for {@link PublicMediaController}.
 *
 * <p>The controller reads straight off the filesystem, so these drive it
 * against a real {@code @TempDir} laid out the way
 * {@code LocalFileStorageService} writes it —
 * {@code {uploadDir}/tenant/{tenantId}/{category}/{filename}} — rather than
 * through MockMvc: what is worth pinning here is which file the walk selects
 * and what happens when it finds nothing, not the URL mapping.</p>
 */
class PublicMediaControllerTest {

    private static final String TENANT = "11111111-1111-1111-1111-111111111111";

    @TempDir
    Path tempDir;

    private PublicMediaController controller;

    @BeforeEach
    void setUp() {
        controller = new PublicMediaController(props(tempDir));
    }

    private static StorageProperties props(Path uploadDir) {
        return new StorageProperties(
                "local", uploadDir.toString(), null, "ap-south-1", null,
                2_097_152L, 4_194_304L);
    }

    /** Writes {@code tenant/{TENANT}/{category}/{filename}} and returns it. */
    private Path givenStoredFile(String category, String filename, byte[] content) throws IOException {
        Path dir = tempDir.resolve("tenant").resolve(TENANT).resolve(category);
        Files.createDirectories(dir);
        Path file = dir.resolve(filename);
        Files.write(file, content);
        return file;
    }

    // ---------------------------------------------------------------- found

    @Test
    void storedFile_isReturnedWithItsContent() throws Exception {
        byte[] content = "binary-logo-bytes".getBytes();
        givenStoredFile("logo", "abc.png", content);

        ResponseEntity<Resource> response = controller.getMediaFile("logo", "abc.png");

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getInputStream().readAllBytes()).isEqualTo(content);
    }

    @Test
    void storedFile_carriesItsProbedContentType() throws Exception {
        givenStoredFile("logo", "abc.png", "png".getBytes());

        ResponseEntity<Resource> response = controller.getMediaFile("logo", "abc.png");

        assertThat(response.getHeaders().getFirst(HttpHeaders.CONTENT_TYPE))
                .isEqualTo("image/png");
    }

    /**
     * The same filename can exist under both categories — the walk must match
     * on the parent directory too, or a logo request could serve the cover.
     */
    @Test
    void sameFilenameInTwoCategories_resolvesByCategory() throws Exception {
        givenStoredFile("logo", "same.png", "the-logo".getBytes());
        givenStoredFile("cover", "same.png", "the-cover".getBytes());

        ResponseEntity<Resource> logo = controller.getMediaFile("logo", "same.png");
        ResponseEntity<Resource> cover = controller.getMediaFile("cover", "same.png");

        assertThat(logo.getBody().getInputStream().readAllBytes()).isEqualTo("the-logo".getBytes());
        assertThat(cover.getBody().getInputStream().readAllBytes()).isEqualTo("the-cover".getBytes());
    }

    /** Files live under a per-tenant directory, so the walk must descend. */
    @Test
    void fileNestedUnderTenantDirectory_isFound() throws Exception {
        givenStoredFile("cover", "deep.webp", "x".getBytes());

        assertThat(controller.getMediaFile("cover", "deep.webp").getStatusCode().value())
                .isEqualTo(200);
    }

    // ---------------------------------------------------------------- not found

    @Test
    void noMediaDirectoryAtAll_is404() {
        // nothing has ever been uploaded, so {uploadDir}/tenant does not exist
        assertThatThrownBy(() -> controller.getMediaFile("logo", "abc.png"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Media directory not found");
    }

    @Test
    void unknownFilename_is404() throws Exception {
        givenStoredFile("logo", "abc.png", "x".getBytes());

        assertThatThrownBy(() -> controller.getMediaFile("logo", "nope.png"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Media file not found");
    }

    @Test
    void knownFilenameUnderADifferentCategory_is404() throws Exception {
        givenStoredFile("logo", "abc.png", "x".getBytes());

        assertThatThrownBy(() -> controller.getMediaFile("cover", "abc.png"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Media file not found");
    }

    /** A directory whose name matches is not a file to serve. */
    @Test
    void directoryMatchingTheRequestedName_is404() throws Exception {
        Files.createDirectories(tempDir.resolve("tenant").resolve(TENANT).resolve("logo").resolve("abc.png"));

        assertThatThrownBy(() -> controller.getMediaFile("logo", "abc.png"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Media file not found");
    }

    // ---------------------------------------------------------------- content type edge cases

    /**
     * {@code probeContentType} is platform-dependent and returns null for a
     * type the host cannot identify — the response must still be well-formed,
     * so it falls back to a generic binary type. Stubbed rather than driven by
     * an odd file extension, which would make the assertion depend on the
     * MIME database of whatever machine runs the build.
     */
    @Test
    void unidentifiableContentType_fallsBackToOctetStream() throws Exception {
        givenStoredFile("logo", "mystery.bin", "x".getBytes());

        try (MockedStatic<Files> files = mockStatic(Files.class, CALLS_REAL_METHODS)) {
            files.when(() -> Files.probeContentType(any(Path.class))).thenReturn(null);

            ResponseEntity<Resource> response = controller.getMediaFile("logo", "mystery.bin");

            assertThat(response.getHeaders().getFirst(HttpHeaders.CONTENT_TYPE))
                    .isEqualTo(MediaType.APPLICATION_OCTET_STREAM_VALUE);
        }
    }

    /** An unreadable file is a 404 like any other, not a 500. */
    @Test
    void ioFailureWhileReading_is404() throws Exception {
        givenStoredFile("logo", "abc.png", "x".getBytes());

        try (MockedStatic<Files> files = mockStatic(Files.class, CALLS_REAL_METHODS)) {
            files.when(() -> Files.probeContentType(any(Path.class)))
                    .thenThrow(new IOException("disk gone"));

            assertThatThrownBy(() -> controller.getMediaFile("logo", "abc.png"))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessage("Could not read media file");
        }
    }

    // ---------------------------------------------------------------- construction

    /** The configured upload dir is resolved once, to an absolute path. */
    @Test
    void relativeUploadDir_isResolvedAgainstTheWorkingDirectory() {
        PublicMediaController relative = new PublicMediaController(props(Path.of("uploads")));

        // No media directory exists under it, which is the reachable proof that
        // the constructor resolved a path at all rather than throwing.
        assertThatThrownBy(() -> relative.getMediaFile("logo", "abc.png"))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
