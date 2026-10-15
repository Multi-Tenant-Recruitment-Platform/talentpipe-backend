package com.talentpipe.common.util;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.talentpipe.common.exception.UnsupportedMediaException;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

class AbstractFileValidatorTest {

    static class ConcreteValidator extends AbstractFileValidator {
        final AtomicBoolean customCalled = new AtomicBoolean(false);

        @Override
        protected long getMaxSizeBytes() {
            return 1024L;
        }

        @Override
        protected Set<String> getAllowedMimeTypes() {
            return Set.of("image/png");
        }

        @Override
        protected Set<String> getAllowedExtensions() {
            return Set.of(".png");
        }

        @Override
        protected String getDescriptor() {
            return "Test file";
        }

        @Override
        protected void validateCustom(MultipartFile file) {
            customCalled.set(true);
        }
    }

    @Test
    void rejectsNullFile() {
        ConcreteValidator validator = new ConcreteValidator();
        assertThatThrownBy(() -> validator.validate(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Test file file is empty");
    }

    @Test
    void rejectsEmptyFile() {
        ConcreteValidator validator = new ConcreteValidator();
        MockMultipartFile file = new MockMultipartFile("f", "test.png", "image/png", new byte[0]);
        assertThatThrownBy(() -> validator.validate(file))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Test file file is empty");
    }

    @Test
    void rejectsOversizedFile() {
        ConcreteValidator validator = new ConcreteValidator();
        MockMultipartFile file = new MockMultipartFile("f", "test.png", "image/png", new byte[2048]);
        assertThatThrownBy(() -> validator.validate(file))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("exceeds the maximum limit");
    }

    @Test
    void rejectsUnsupportedMimeType() {
        ConcreteValidator validator = new ConcreteValidator();
        MockMultipartFile file = new MockMultipartFile("f", "test.png", "image/gif", new byte[100]);
        assertThatThrownBy(() -> validator.validate(file))
                .isInstanceOf(UnsupportedMediaException.class)
                .hasMessageContaining("Unsupported test file format: 'image/gif'");
    }

    @Test
    void rejectsNullFilename() {
        ConcreteValidator validator = new ConcreteValidator();
        MockMultipartFile file = new MockMultipartFile("f", null, "image/png", new byte[100]);
        assertThatThrownBy(() -> validator.validate(file))
                .isInstanceOf(UnsupportedMediaException.class)
                .hasMessageContaining("Unsupported file extension");
    }

    @Test
    void rejectsUnsupportedExtension() {
        ConcreteValidator validator = new ConcreteValidator();
        MockMultipartFile file = new MockMultipartFile("f", "test.jpg", "image/png", new byte[100]);
        assertThatThrownBy(() -> validator.validate(file))
                .isInstanceOf(UnsupportedMediaException.class)
                .hasMessageContaining("Unsupported file extension for test file");
    }

    @Test
    void allowsNullOrBlankMimeTypeIfExtensionMatches() {
        ConcreteValidator validator = new ConcreteValidator();
        MockMultipartFile nullMime = new MockMultipartFile("f", "test.png", null, new byte[100]);
        assertThatCode(() -> validator.validate(nullMime)).doesNotThrowAnyException();

        MockMultipartFile blankMime = new MockMultipartFile("f", "test.png", "   ", new byte[100]);
        assertThatCode(() -> validator.validate(blankMime)).doesNotThrowAnyException();
    }

    @Test
    void executesValidateCustomHook() {
        ConcreteValidator validator = new ConcreteValidator();
        MockMultipartFile valid = new MockMultipartFile("f", "test.png", "image/png", new byte[100]);
        validator.validate(valid);
        org.assertj.core.api.Assertions.assertThat(validator.customCalled.get()).isTrue();
    }

    @Test
    void matchesPrefix_checksAllBranches() {
        ConcreteValidator validator = new ConcreteValidator();
        byte[] prefix = new byte[]{0x01, 0x02};

        org.assertj.core.api.Assertions.assertThat(validator.matchesPrefix(null, prefix)).isFalse();
        org.assertj.core.api.Assertions.assertThat(validator.matchesPrefix(new byte[]{0x01}, prefix)).isFalse();
        org.assertj.core.api.Assertions.assertThat(validator.matchesPrefix(new byte[]{0x01, 0x03}, prefix)).isFalse();
        org.assertj.core.api.Assertions.assertThat(validator.matchesPrefix(new byte[]{0x01, 0x02, 0x03}, prefix)).isTrue();
    }

    @Test
    void matchesBytesAt_checksAllBranches() {
        ConcreteValidator validator = new ConcreteValidator();
        byte[] pattern = new byte[]{0x0A, 0x0B};

        org.assertj.core.api.Assertions.assertThat(validator.matchesBytesAt(null, pattern, 0)).isFalse();
        org.assertj.core.api.Assertions.assertThat(validator.matchesBytesAt(new byte[]{0x0A}, pattern, 0)).isFalse();
        org.assertj.core.api.Assertions.assertThat(validator.matchesBytesAt(new byte[]{0x00, 0x0A, 0x0C}, pattern, 1)).isFalse();
        org.assertj.core.api.Assertions.assertThat(validator.matchesBytesAt(new byte[]{0x00, 0x0A, 0x0B}, pattern, 1)).isTrue();
    }

    @Test
    void readHeaderBytes_returnsExpectedBytesOrThrows() {
        ConcreteValidator validator = new ConcreteValidator();
        MockMultipartFile file = new MockMultipartFile("f", "test.png", "image/png", new byte[]{1, 2, 3, 4, 5});
        byte[] header = validator.readHeaderBytes(file, 3);
        org.assertj.core.api.Assertions.assertThat(header).containsExactly(1, 2, 3);

        MockMultipartFile faultyFile = new MockMultipartFile("f", "test.png", "image/png", new byte[]{1}) {
            @Override
            public java.io.InputStream getInputStream() throws java.io.IOException {
                throw new java.io.IOException("Stream failure");
            }
        };
        assertThatThrownBy(() -> validator.readHeaderBytes(faultyFile, 4))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Could not read Test file file header");
    }
}
