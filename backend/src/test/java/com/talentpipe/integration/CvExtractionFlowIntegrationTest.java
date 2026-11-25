package com.talentpipe.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.talentpipe.ai.CvTextExtractor;
import com.talentpipe.ai.ExtractedCvResult;
import com.talentpipe.ai.UnreadableCvException;
import com.talentpipe.candidate.entity.CandidateCv;
import com.talentpipe.candidate.extraction.CvExtractionRecorder;
import com.talentpipe.candidate.repository.CandidateCvRepository;
import com.talentpipe.integration.CvExtractionFlowIntegrationTest.ExtractorConfig.FakeExtractor;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.ResourceAccessException;

/**
 * A CV from upload to application, through the HTTP API as a candidate uses it:
 * its text is extracted in the background whatever the AI service does, and an
 * application keeps the CV it was made with.
 *
 * <p>The AI service is a stand-in for the adapter of MRP-359, behind the same
 * {@link CvTextExtractor} port.</p>
 *
 * <p>A CV is replaced here by removing it and then uploading the next one, as two
 * requests.</p>
 */
@Import(CvExtractionFlowIntegrationTest.ExtractorConfig.class)
class CvExtractionFlowIntegrationTest extends AbstractJobIntegrationTest {

    private static final String CV_TEXT = "Kasun Perera. Seven years of Java and Spring Boot.";

    @TempDir
    static Path uploads;

    @DynamicPropertySource
    static void uploadsGoToATemporaryDirectory(DynamicPropertyRegistry registry) {
        registry.add("talentpipe.storage.upload-dir", () -> uploads.toString());
    }

    @Autowired
    private FakeExtractor extractor;

    @Autowired
    private CandidateCvRepository cvs;

    @Autowired
    private CvExtractionRecorder recorder;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private String candidate;

    @BeforeEach
    void aSignedInCandidate() {
        extractor.reset();
        candidate = newCandidate();
    }

    // -------------------------------------------------------------- extraction

    @Test
    void anUploadedCv_isExtracted_andItsTextIsNeverReturned() {
        extractor.answers(file -> new ExtractedCvResult(CV_TEXT, CV_TEXT.length(), 1, false));

        ResponseEntity<String> uploaded = upload("kasun.pdf", "first");

        assertThat(uploaded.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> cv = cvOnceItIs("EXTRACTED");
        assertThat(cv).containsEntry("extracted_text", CV_TEXT);
        assertThat(cv.get("text_extracted_at")).isNotNull();

        // What the extractor was given is the file that was uploaded.
        assertThat(extractor.files()).containsExactly(new SeenFile("%PDF-1.4 first", "kasun.pdf", "application/pdf"));

        String profile = rest.exchange("/api/v1/candidates/me", HttpMethod.GET,
                new HttpEntity<>(bearer(candidate)), String.class).getBody();
        assertThat(profile).contains("kasun.pdf").doesNotContain("Seven years").doesNotContainIgnoringCase("extract");
        assertThat(uploaded.getBody()).doesNotContain("Seven years").doesNotContainIgnoringCase("extract");
    }

    @Test
    void theUploadDoesNotWaitForTheAiService_andNoTransactionIsOpenWhileItWorks() throws Exception {
        CountDownLatch working = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        extractor.answers(file -> {
            working.countDown();
            waitFor(release);
            return new ExtractedCvResult(CV_TEXT, CV_TEXT.length(), 1, false);
        });
        try {
            assertThat(upload("kasun.pdf", "first").getStatusCode()).isEqualTo(HttpStatus.OK);

            // Answered, and stored, while the service is still at work.
            assertThat(working.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(onlyCv()).containsEntry("extraction_status", "PENDING");
        } finally {
            release.countDown();
        }
        cvOnceItIs("EXTRACTED");
        assertThat(extractor.transactionsSeen()).containsExactly(false);
    }

    @Test
    void whenTheAiServiceIsDown_theUploadStillWorks_andTheCvStaysPending() {
        extractor.answers(file -> {
            throw new ResourceAccessException("Connection refused");
        });

        assertThat(upload("kasun.pdf", "first").getStatusCode()).isEqualTo(HttpStatus.OK);

        extractor.awaitCalls(1);
        assertThat(onlyCv())
                .containsEntry("extraction_status", "PENDING")
                .containsEntry("extracted_text", null)
                .containsEntry("is_active", true);
        // The CV itself is there as always.
        assertThat(rest.exchange("/api/v1/candidates/me/cv/download", HttpMethod.GET,
                new HttpEntity<>(bearer(candidate)), String.class).getBody()).isEqualTo("%PDF-1.4 first");
    }

    @Test
    void aScan_isMarkedForAPersonToLookAt() {
        extractor.answers(file -> new ExtractedCvResult("", 0, 3, true));

        upload("scan.pdf", "scanned");

        assertThat(cvOnceItIs("EMPTY_OR_SCANNED")).containsEntry("extracted_text", "");
    }

    @Test
    void aFileThatCannotBeRead_isMarkedFailed() {
        extractor.answers(file -> {
            throw new UnreadableCvException("encrypted");
        });

        upload("locked.pdf", "encrypted");

        assertThat(cvOnceItIs("FAILED")).containsEntry("extracted_text", null);
    }

    @Test
    void aTextPostgresCouldNotStore_isStoredWithoutWhatItCannotHold() {
        extractor.answers(file -> new ExtractedCvResult("Java\0Spring", 11, 1, false));

        upload("kasun.pdf", "first");

        assertThat(cvOnceItIs("EXTRACTED")).containsEntry("extracted_text", "JavaSpring");
    }

    @Test
    void theSameFile_isExtractedOnce() {
        extractor.answers(file -> new ExtractedCvResult(CV_TEXT, CV_TEXT.length(), 1, false));
        upload("kasun.pdf", "first");
        cvOnceItIs("EXTRACTED");
        removeCv();

        upload("kasun-again.pdf", "first");

        await().atMost(Duration.ofSeconds(5)).until(() -> extractedCvs() == 2);
        assertThat(extractor.files()).hasSize(1);
        assertThat(jdbc.queryForList("SELECT extracted_text FROM candidate_cvs", String.class))
                .containsExactly(CV_TEXT, CV_TEXT);
    }

    /** Without @DynamicUpdate, whichever of the two finished last would put the other's columns back. */
    @Test
    void archivingACv_whileItIsBeingExtracted_losesNeither() {
        extractor.answers(file -> {
            throw new ResourceAccessException("Connection refused");
        });
        upload("kasun.pdf", "first");
        extractor.awaitCalls(1);
        UUID cvId = (UUID) onlyCv().get("id");

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            CandidateCv cv = cvs.findById(cvId).orElseThrow();
            // In a transaction of its own, committed before the archiving one.
            recorder.recordExtracted(cvId, new ExtractedCvResult(CV_TEXT, CV_TEXT.length(), 1, false));
            cv.archive(Instant.now());
        });

        assertThat(onlyCv())
                .containsEntry("extraction_status", "EXTRACTED")
                .containsEntry("extracted_text", CV_TEXT)
                .containsEntry("is_active", false);
    }

    // ------------------------------------------------------------ applications

    @Test
    void anApplicationKeepsTheCvItWasMadeWith_whenTheCandidateReplacesIt() {
        Workspace acme = newWorkspace("Acme");
        Object firstJob = createPublished(acme.adminToken(), "Backend Engineer").get("id");
        Object secondJob = createPublished(acme.adminToken(), "Platform Engineer").get("id");
        upload("first.pdf", "first");
        UUID firstCv = activeCv();

        Object firstApplication = apply(firstJob);
        removeCv();
        upload("second.pdf", "second");
        UUID secondCv = activeCv();
        Object secondApplication = apply(secondJob);

        assertThat(secondCv).isNotEqualTo(firstCv);
        assertThat(cvOf(firstApplication)).isEqualTo(firstCv);
        assertThat(cvOf(secondApplication)).isEqualTo(secondCv);

        // Nor does anything done to the application later change it.
        call(HttpMethod.DELETE, "/api/v1/applications/" + firstApplication, candidate, null);
        assertThat(cvOf(firstApplication)).isEqualTo(firstCv);
    }

    @Test
    void aCandidateWithoutACv_canStillApply() {
        Workspace acme = newWorkspace("Acme");
        Object job = createPublished(acme.adminToken(), "Backend Engineer").get("id");

        assertThat(cvOf(apply(job))).isNull();
    }

    @Test
    void aCvTheCandidateRemoved_isNotSubmitted() {
        Workspace acme = newWorkspace("Acme");
        Object job = createPublished(acme.adminToken(), "Backend Engineer").get("id");
        upload("first.pdf", "first");
        removeCv();

        assertThat(cvOf(apply(job))).isNull();
    }

    // ------------------------------------------------------------------ helpers

    /** Registers a candidate, verifies their email and signs them in. */
    private String newCandidate() {
        String email = "kasun-" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
        call(HttpMethod.POST, "/api/v1/public/candidates/register", null, Map.of(
                "fullName", "Kasun Perera",
                "identityCardNumber", "200012345678",
                "address", "Colombo",
                "contactNumber", "+94 77 123 4567",
                "email", email,
                "password", PASSWORD));
        rest.postForEntity("/api/v1/auth/verify-email",
                json(Map.of("token", tokenIn(emailCapture.lastVerificationLink())), null), String.class);
        emailCapture.clear();
        ResponseEntity<Map<String, Object>> login = loginResponse(null, email, PASSWORD);
        assertThat(login.getStatusCode()).as("login: %s", login.getBody()).isEqualTo(HttpStatus.OK);
        return (String) login.getBody().get("accessToken");
    }

    /** Uploads a CV through the profile form. */
    private ResponseEntity<String> upload(String fileName, String content) {
        HttpHeaders pdf = new HttpHeaders();
        pdf.setContentType(MediaType.APPLICATION_PDF);
        byte[] bytes = ("%PDF-1.4 " + content).getBytes(StandardCharsets.UTF_8);
        MultiValueMap<String, Object> form = profileForm();
        form.add("cv", new HttpEntity<>(new ByteArrayResource(bytes) {
            @Override
            public String getFilename() {
                return fileName;
            }
        }, pdf));
        return save(form);
    }

    private void removeCv() {
        MultiValueMap<String, Object> form = profileForm();
        form.add("removeCv", "true");
        assertThat(save(form).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    private MultiValueMap<String, Object> profileForm() {
        Map<String, Object> me = call(HttpMethod.GET, "/api/v1/candidates/me", candidate, null).getBody();
        MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
        form.add("fullName", me.get("fullName"));
        form.add("email", me.get("email"));
        form.add("identityCardNumber", "200012345678");
        form.add("phone", "+94 77 123 4567");
        form.add("addToTalentPool", "true");
        return form;
    }

    private ResponseEntity<String> save(MultiValueMap<String, Object> form) {
        HttpHeaders headers = bearer(candidate);
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        return rest.exchange("/api/v1/candidates/me", HttpMethod.PUT, new HttpEntity<>(form, headers), String.class);
    }

    private Object apply(Object jobId) {
        ResponseEntity<Map<String, Object>> response =
                call(HttpMethod.POST, "/api/v1/applications/jobs/" + jobId, candidate, null);
        assertThat(response.getStatusCode()).as("apply: %s", response.getBody()).isEqualTo(HttpStatus.CREATED);
        return response.getBody().get("id");
    }

    private Map<String, Object> onlyCv() {
        return jdbc.queryForMap("SELECT * FROM candidate_cvs");
    }

    private Map<String, Object> cvOnceItIs(String status) {
        await().atMost(Duration.ofSeconds(5)).until(() -> status.equals(onlyCv().get("extraction_status")));
        return onlyCv();
    }

    private long extractedCvs() {
        return jdbc.queryForObject(
                "SELECT count(*) FROM candidate_cvs WHERE extraction_status = 'EXTRACTED'", Long.class);
    }

    private UUID activeCv() {
        return jdbc.queryForObject("SELECT id FROM candidate_cvs WHERE is_active", UUID.class);
    }

    private UUID cvOf(Object applicationId) {
        return jdbc.queryForObject("SELECT cv_id FROM applications WHERE id = ?::uuid", UUID.class, applicationId);
    }

    private static void waitFor(CountDownLatch latch) {
        try {
            latch.await(10, TimeUnit.SECONDS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    /** A file the extractor was given. */
    record SeenFile(String content, String fileName, String mimeType) {
    }

    /** Stands in for the AI service's adapter (MRP-359). */
    @TestConfiguration
    static class ExtractorConfig {

        @Bean
        FakeExtractor fakeExtractor() {
            return new FakeExtractor();
        }

        static class FakeExtractor implements CvTextExtractor {

            private final List<SeenFile> files = new CopyOnWriteArrayList<>();
            private final List<Boolean> transactions = new CopyOnWriteArrayList<>();
            private volatile Function<SeenFile, ExtractedCvResult> answer;

            void reset() {
                files.clear();
                transactions.clear();
                answers(file -> new ExtractedCvResult(CV_TEXT, CV_TEXT.length(), 1, false));
            }

            void answers(Function<SeenFile, ExtractedCvResult> answer) {
                this.answer = answer;
            }

            List<SeenFile> files() {
                return List.copyOf(files);
            }

            /** For each call, whether a transaction was open. */
            List<Boolean> transactionsSeen() {
                return List.copyOf(transactions);
            }

            void awaitCalls(int calls) {
                await().atMost(Duration.ofSeconds(5)).until(() -> files.size() == calls);
            }

            @Override
            public ExtractedCvResult extract(byte[] fileBytes, String filename, String mimeType) {
                SeenFile file = new SeenFile(new String(fileBytes, StandardCharsets.UTF_8), filename, mimeType);
                transactions.add(TransactionSynchronizationManager.isActualTransactionActive());
                files.add(file);
                return answer.apply(file);
            }
        }
    }
}
