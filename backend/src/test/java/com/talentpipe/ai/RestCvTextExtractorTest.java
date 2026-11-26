package com.talentpipe.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withBadRequest;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.talentpipe.ai.dto.ExtractedCvResult;
import java.nio.charset.StandardCharsets;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.autoconfigure.web.client.RestClientTest;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.HttpClientErrorException;

@RestClientTest(RestCvTextExtractor.class)
@EnableConfigurationProperties(AiServiceProperties.class)
@TestPropertySource(properties = "talentpipe.ai.base-url=http://ai-service:8000")
class RestCvTextExtractorTest {

    private static final String EXTRACT_PATH = "/api/v1/extract";

    @Autowired
    private CvTextExtractor extractor;

    @Autowired
    private MockRestServiceServer mockServer;

    @Test
    void extract_uploadsMultipartFileAndParsesResponse() {
        mockServer.expect(requestTo(EXTRACT_PATH))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("""
                        {
                          "extracted_text": "Clean Canva PDF CV text content",
                          "char_count": 31,
                          "page_count": 1,
                          "format": "PDF",
                          "extractor": "pymupdf4llm",
                          "empty_or_scanned": false
                        }
                        """, MediaType.APPLICATION_JSON));

        ExtractedCvResult result = extractor.extract(
                "%PDF-1.4".getBytes(StandardCharsets.US_ASCII),
                "resume.pdf",
                MediaType.APPLICATION_PDF_VALUE);

        mockServer.verify();
        assertThat(result.extractedText()).isEqualTo("Clean Canva PDF CV text content");
        assertThat(result.charCount()).isEqualTo(31);
        assertThat(result.format()).isEqualTo("PDF");
        assertThat(result.emptyOrScanned()).isFalse();
    }

    @ParameterizedTest
    @MethodSource("missingFileMetadata")
    void extract_usesFallbackFilenameAndOctetStreamWhenMetadataMissing(String filename, String mimeType) {
        mockServer.expect(requestTo(EXTRACT_PATH))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().string(containsString("filename=\"cv.bin\"")))
                .andExpect(content().string(containsString("Content-Type: application/octet-stream")))
                .andRespond(withSuccess("""
                        {
                          "extracted_text": "Fallback metadata text",
                          "char_count": 22,
                          "page_count": 1,
                          "format": "DOCX",
                          "extractor": "python-docx",
                          "empty_or_scanned": false
                        }
                        """, MediaType.APPLICATION_JSON));

        ExtractedCvResult result = extractor.extract(
                "PK\u0003\u0004".getBytes(StandardCharsets.US_ASCII),
                filename,
                mimeType);

        mockServer.verify();
        assertThat(result.format()).isEqualTo("DOCX");
    }

    static Stream<Arguments> missingFileMetadata() {
        return Stream.of(
                Arguments.of(null, null),
                Arguments.of("   ", "   "));
    }

    @Test
    void extract_rejectsNullOrEmptyBytesWithoutHttpCall() {
        byte[] empty = new byte[0];

        assertThatThrownBy(() -> extractor.extract(empty, "empty.pdf", "application/pdf"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> extractor.extract(null, "null.pdf", "application/pdf"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void extract_propagatesClientError() {
        mockServer.expect(requestTo(EXTRACT_PATH))
                .andRespond(withBadRequest());

        byte[] payload = "invalid".getBytes(StandardCharsets.UTF_8);

        assertThatThrownBy(() -> extractor.extract(payload, "bad.txt", "text/plain"))
                .isInstanceOf(HttpClientErrorException.class);
        mockServer.verify();
    }
}
