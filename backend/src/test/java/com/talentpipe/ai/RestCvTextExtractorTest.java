package com.talentpipe.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.talentpipe.ai.dto.CvExtractionResponse;
import java.nio.charset.StandardCharsets;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.autoconfigure.web.client.RestClientTest;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;

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
    void extract_uploadsMultipartFileAndMapsDomainResult() {
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
        assertThat(result.text()).isEqualTo("Clean Canva PDF CV text content");
        assertThat(result.charCount()).isEqualTo(31);
        assertThat(result.pageCount()).isEqualTo(1);
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
        assertThat(result.text()).isEqualTo("Fallback metadata text");
    }

    static Stream<Arguments> missingFileMetadata() {
        return Stream.of(
                Arguments.of(null, null),
                Arguments.of("   ", "   "));
    }

    @Test
    void extract_returnsNullWhenBodyIsEmpty() {
        mockServer.expect(requestTo(EXTRACT_PATH))
                .andRespond(withSuccess("", MediaType.APPLICATION_JSON));

        ExtractedCvResult result = extractor.extract(
                "%PDF-1.4".getBytes(StandardCharsets.US_ASCII),
                "empty-body.pdf",
                MediaType.APPLICATION_PDF_VALUE);

        mockServer.verify();
        assertThat(result).isNull();
    }

    @Test
    void extract_rejectsNullOrEmptyBytesAsUnreadableWithoutHttpCall() {
        byte[] empty = new byte[0];

        assertThatThrownBy(() -> extractor.extract(empty, "empty.pdf", MediaType.APPLICATION_PDF_VALUE))
                .isInstanceOf(UnreadableCvException.class);
        assertThatThrownBy(() -> extractor.extract(null, "null.pdf", MediaType.APPLICATION_PDF_VALUE))
                .isInstanceOf(UnreadableCvException.class);
    }

    @ParameterizedTest
    @ValueSource(ints = {400, 413, 415, 422})
    void extract_mapsPermanentClientRejectionsToUnreadableCvException(int statusCode) {
        mockServer.expect(requestTo(EXTRACT_PATH))
                .andRespond(withStatus(HttpStatus.valueOf(statusCode)));

        byte[] payload = "corrupt".getBytes(StandardCharsets.UTF_8);

        assertThatThrownBy(() -> extractor.extract(payload, "bad.cv", MediaType.APPLICATION_OCTET_STREAM_VALUE))
                .isInstanceOf(UnreadableCvException.class)
                .hasMessageContaining(String.valueOf(statusCode));
        mockServer.verify();
    }

    @Test
    void extract_propagatesTransientClientAndServerErrorsForRetry() {
        mockServer.expect(requestTo(EXTRACT_PATH))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));
        byte[] payload = "%PDF-1.4".getBytes(StandardCharsets.US_ASCII);

        assertThatThrownBy(() -> extractor.extract(payload, "rate.pdf", MediaType.APPLICATION_PDF_VALUE))
                .isInstanceOf(HttpClientErrorException.class);
        mockServer.verify();

        mockServer.reset();
        mockServer.expect(requestTo(EXTRACT_PATH))
                .andRespond(withServerError());

        assertThatThrownBy(() -> extractor.extract(payload, "err.pdf", MediaType.APPLICATION_PDF_VALUE))
                .isInstanceOf(HttpServerErrorException.class);
        mockServer.verify();
    }

    @Test
    void wireResponse_omitsExtractedTextFromToString() {
        CvExtractionResponse response = new CvExtractionResponse(
                "Sensitive Candidate Name", 24, 1, "PDF", "pymupdf4llm", false);

        assertThat(response.toString())
                .doesNotContain("Sensitive")
                .contains("charCount=24", "format=PDF", "extractor=pymupdf4llm");
    }
}
