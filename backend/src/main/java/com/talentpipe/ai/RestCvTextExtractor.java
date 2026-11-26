package com.talentpipe.ai;

import com.talentpipe.ai.dto.ExtractedCvResult;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

/**
 * Multipart REST adapter delegating PDF/DOCX CV extraction to the Python AI service.
 */
@Component
public class RestCvTextExtractor implements CvTextExtractor {

    private static final String DEFAULT_FILENAME = "cv.bin";

    private final RestTemplate restTemplate;

    public RestCvTextExtractor(AiServiceProperties properties, RestTemplateBuilder builder) {
        this.restTemplate = AiRestTemplates.build(properties, builder);
    }

    @Override
    public ExtractedCvResult extract(byte[] fileBytes, String filename, String mimeType) {
        if (fileBytes == null || fileBytes.length == 0) {
            throw new IllegalArgumentException("CV file content must not be empty");
        }
        HttpEntity<MultiValueMap<String, Object>> requestEntity = buildMultipartEntity(
                fileBytes, safeFilename(filename), resolveMediaType(mimeType));
        return restTemplate.postForObject("/api/v1/extract", requestEntity, ExtractedCvResult.class);
    }

    private HttpEntity<MultiValueMap<String, Object>> buildMultipartEntity(
            byte[] fileBytes, String safeName, MediaType partMediaType) {
        HttpHeaders partHeaders = new HttpHeaders();
        partHeaders.setContentType(partMediaType);
        ByteArrayResource resource = new ByteArrayResource(fileBytes) {
            @Override
            public String getFilename() {
                return safeName;
            }
        };
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", new HttpEntity<>(resource, partHeaders));

        HttpHeaders requestHeaders = new HttpHeaders();
        requestHeaders.setContentType(MediaType.MULTIPART_FORM_DATA);
        return new HttpEntity<>(body, requestHeaders);
    }

    private String safeFilename(String filename) {
        return (filename == null || filename.isBlank()) ? DEFAULT_FILENAME : filename;
    }

    private MediaType resolveMediaType(String mimeType) {
        if (mimeType == null || mimeType.isBlank()) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
        return MediaType.parseMediaType(mimeType);
    }
}
