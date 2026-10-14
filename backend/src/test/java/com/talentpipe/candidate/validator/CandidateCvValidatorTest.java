package com.talentpipe.candidate.validator;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.talentpipe.common.exception.UnsupportedMediaException;
import com.talentpipe.common.storage.StorageProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

@ExtendWith(MockitoExtension.class)
class CandidateCvValidatorTest {

    @Mock
    private StorageProperties storageProperties;

    private CandidateCvValidator validator;

    @BeforeEach
    void setUp() {
        org.mockito.Mockito.lenient().when(storageProperties.maxCvBytes()).thenReturn(5L * 1024 * 1024);
        validator = new CandidateCvValidator(storageProperties);
    }

    @Test
    void acceptsValidPdf() {
        byte[] pdfBytes = new byte[]{0x25, 0x50, 0x44, 0x46, 0x2D, 0x31, 0x2E, 0x34}; // "%PDF-1.4"
        MockMultipartFile file = new MockMultipartFile(
                "cv",
                "resume.pdf",
                "application/pdf",
                pdfBytes);

        assertThatCode(() -> validator.validate(file)).doesNotThrowAnyException();
    }

    @Test
    void acceptsValidDocx() {
        byte[] docxBytes = new byte[]{0x50, 0x4B, 0x03, 0x04, 0x00, 0x00}; // "PK\x03\x04"
        MockMultipartFile file = new MockMultipartFile(
                "cv",
                "resume.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                docxBytes);

        assertThatCode(() -> validator.validate(file)).doesNotThrowAnyException();
    }

    @Test
    void rejectsEmptyCv() {
        MockMultipartFile file = new MockMultipartFile(
                "cv",
                "resume.pdf",
                "application/pdf",
                new byte[0]);

        assertThatThrownBy(() -> validator.validate(file))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("CV file is empty");
    }

    @Test
    void rejectsOversizedCv() {
        byte[] pdfHeader = new byte[]{0x25, 0x50, 0x44, 0x46};
        byte[] bigData = new byte[(6 * 1024 * 1024)];
        System.arraycopy(pdfHeader, 0, bigData, 0, pdfHeader.length);

        MockMultipartFile file = new MockMultipartFile(
                "cv",
                "resume.pdf",
                "application/pdf",
                bigData);

        assertThatThrownBy(() -> validator.validate(file))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("exceeds the maximum limit");
    }

    @Test
    void rejectsPdfWithInvalidMagicBytes() {
        byte[] fakeBytes = new byte[]{0x00, 0x01, 0x02, 0x03, 0x04};
        MockMultipartFile file = new MockMultipartFile(
                "cv",
                "fake.pdf",
                "application/pdf",
                fakeBytes);

        assertThatThrownBy(() -> validator.validate(file))
                .isInstanceOf(UnsupportedMediaException.class)
                .hasMessageContaining("does not match a valid PDF format");
    }

    @Test
    void rejectsDocxWithInvalidMagicBytes() {
        byte[] fakeBytes = new byte[]{0x00, 0x01, 0x02, 0x03, 0x04};
        MockMultipartFile file = new MockMultipartFile(
                "cv",
                "fake.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                fakeBytes);

        assertThatThrownBy(() -> validator.validate(file))
                .isInstanceOf(UnsupportedMediaException.class)
                .hasMessageContaining("does not match a valid DOCX format");
    }

    @Test
    void rejectsCvWhenHeaderReadThrowsIOException() throws Exception {
        org.springframework.web.multipart.MultipartFile mockFile = org.mockito.Mockito.mock(org.springframework.web.multipart.MultipartFile.class);
        when(mockFile.isEmpty()).thenReturn(false);
        when(mockFile.getSize()).thenReturn(1024L);
        when(mockFile.getContentType()).thenReturn("application/pdf");
        when(mockFile.getOriginalFilename()).thenReturn("resume.pdf");
        when(mockFile.getInputStream()).thenThrow(new java.io.IOException("Stream error"));

        assertThatThrownBy(() -> validator.validate(mockFile))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Could not read CV file header");
    }

    @Test
    void rejectsCvWhenHeaderIsTooShort() {
        byte[] shortBytes = new byte[]{0x25}; // shorter than 4-byte prefix
        MockMultipartFile file = new MockMultipartFile(
                "cv",
                "short.pdf",
                "application/pdf",
                shortBytes);

        assertThatThrownBy(() -> validator.validate(file))
                .isInstanceOf(UnsupportedMediaException.class)
                .hasMessageContaining("does not match a valid PDF format");
    }
}
