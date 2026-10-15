package com.talentpipe.candidate.validator;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.talentpipe.common.exception.UnsupportedMediaException;
import com.talentpipe.common.storage.StorageProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

class CandidatePhotoValidatorTest {

    private CandidatePhotoValidator validator;

    @BeforeEach
    void setUp() {
        StorageProperties storageProperties = new StorageProperties(
                "local", "uploads", null, "ap-south-1", null, 0, 0, 2L * 1024 * 1024, 0);
        validator = new CandidatePhotoValidator(storageProperties);
    }

    @Test
    void acceptsValidImage() {
        byte[] pngBytes = new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};
        MockMultipartFile file = new MockMultipartFile(
                "photo",
                "avatar.png",
                "image/png",
                pngBytes);

        assertThatCode(() -> validator.validate(file)).doesNotThrowAnyException();
    }

    @Test
    void acceptsValidJpeg() {
        byte[] jpegBytes = new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0};
        MockMultipartFile file = new MockMultipartFile(
                "photo",
                "avatar.jpg",
                "image/jpeg",
                jpegBytes);

        assertThatCode(() -> validator.validate(file)).doesNotThrowAnyException();
    }

    @Test
    void acceptsValidWebp() {
        byte[] webpBytes = new byte[]{
                0x52, 0x49, 0x46, 0x46, // RIFF
                0x20, 0x00, 0x00, 0x00, // size
                0x57, 0x45, 0x42, 0x50  // WEBP
        };
        MockMultipartFile file = new MockMultipartFile(
                "photo",
                "avatar.webp",
                "image/webp",
                webpBytes);

        assertThatCode(() -> validator.validate(file)).doesNotThrowAnyException();
    }

    @Test
    void rejectsEmptyFile() {
        MockMultipartFile emptyFile = new MockMultipartFile(
                "photo",
                "avatar.png",
                "image/png",
                new byte[0]);

        assertThatThrownBy(() -> validator.validate(emptyFile))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Photo file is empty");
    }

    @Test
    void rejectsOversizedFile() {
        byte[] bigData = new byte[(3 * 1024 * 1024)];
        bigData[0] = (byte) 0xFF;
        bigData[1] = (byte) 0xD8;
        bigData[2] = (byte) 0xFF;
        MockMultipartFile oversized = new MockMultipartFile(
                "photo",
                "avatar.jpg",
                "image/jpeg",
                bigData);

        assertThatThrownBy(() -> validator.validate(oversized))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("exceeds the maximum limit");
    }

    @Test
    void rejectsDisallowedMimeType() {
        MockMultipartFile file = new MockMultipartFile(
                "photo",
                "avatar.gif",
                "image/gif",
                new byte[]{1, 2, 3});

        assertThatThrownBy(() -> validator.validate(file))
                .isInstanceOf(UnsupportedMediaException.class)
                .hasMessageContaining("Unsupported photo format");
    }

    @Test
    void rejectsSvgUploadForSecurity() {
        MockMultipartFile file = new MockMultipartFile(
                "photo",
                "avatar.svg",
                "image/svg+xml",
                "<svg><script>alert(1)</script></svg>".getBytes());

        assertThatThrownBy(() -> validator.validate(file))
                .isInstanceOf(UnsupportedMediaException.class)
                .hasMessageContaining("Unsupported photo format");
    }

    @Test
    void rejectsDisallowedExtension() {
        MockMultipartFile file = new MockMultipartFile(
                "photo",
                "malicious.exe",
                "image/png",
                new byte[]{1, 2, 3});

        assertThatThrownBy(() -> validator.validate(file))
                .isInstanceOf(UnsupportedMediaException.class)
                .hasMessageContaining("Unsupported file extension");
    }

    @Test
    void rejectsPngWithFakeHeader() {
        MockMultipartFile file = new MockMultipartFile(
                "photo",
                "fake.png",
                "image/png",
                new byte[]{1, 2, 3, 4});

        assertThatThrownBy(() -> validator.validate(file))
                .isInstanceOf(UnsupportedMediaException.class)
                .hasMessageContaining("does not match a valid PNG image format");
    }

    @Test
    void rejectsJpegWithFakeHeader() {
        MockMultipartFile file = new MockMultipartFile(
                "photo",
                "fake.jpg",
                "image/jpeg",
                new byte[]{1, 2, 3, 4});

        assertThatThrownBy(() -> validator.validate(file))
                .isInstanceOf(UnsupportedMediaException.class)
                .hasMessageContaining("does not match a valid JPEG image format");
    }
}
