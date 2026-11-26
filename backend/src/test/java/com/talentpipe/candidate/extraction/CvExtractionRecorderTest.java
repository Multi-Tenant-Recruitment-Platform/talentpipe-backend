package com.talentpipe.candidate.extraction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.talentpipe.ai.ExtractedCvResult;
import com.talentpipe.candidate.entity.CandidateCv;
import com.talentpipe.candidate.entity.CvExtractionStatus;
import com.talentpipe.candidate.extraction.CvExtractionRecorder.CvFile;
import com.talentpipe.candidate.repository.CandidateCvRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CvExtractionRecorderTest {

    @Mock
    private CandidateCvRepository cvRepository;

    private final UUID cvId = UUID.randomUUID();

    private CandidateCv cv;
    private CvExtractionRecorder recorder;

    @BeforeEach
    void setUp() {
        cv = cv();
        recorder = new CvExtractionRecorder(cvRepository);
    }

    private static CandidateCv cv() {
        return new CandidateCv(UUID.randomUUID(), "cv.pdf", "candidate/1/cvs/a.pdf", "application/pdf", 10L, "hash", 1);
    }

    private void stored() {
        when(cvRepository.findById(cvId)).thenReturn(Optional.of(cv));
    }

    @Test
    void aPendingCv_isToBeExtracted() {
        stored();

        assertThat(recorder.findFileToExtract(cvId))
                .contains(new CvFile("candidate/1/cvs/a.pdf", "cv.pdf", "application/pdf"));
        assertThat(cv.getExtractionStatus()).isEqualTo(CvExtractionStatus.PENDING);
    }

    @Test
    void aCvThatIsGone_isNot() {
        when(cvRepository.findById(cvId)).thenReturn(Optional.empty());

        assertThat(recorder.findFileToExtract(cvId)).isEmpty();
    }

    @Test
    void aCvWithAResult_isNot_andNoOtherIsLookedFor() {
        cv.recordExtraction("text", false, Instant.now());
        stored();

        assertThat(recorder.findFileToExtract(cvId)).isEmpty();
        verify(cvRepository, never()).findFirstByFileHashSha256AndExtractionStatusIn(any(), any());
    }

    @Test
    void aFileReadBefore_isNotReadAgain() {
        CandidateCv identical = cv();
        identical.recordExtraction("Backend engineer", false, Instant.now());
        stored();
        when(cvRepository.findFirstByFileHashSha256AndExtractionStatusIn(eq("hash"),
                eq(List.of(CvExtractionStatus.EXTRACTED, CvExtractionStatus.EMPTY_OR_SCANNED))))
                .thenReturn(Optional.of(identical));

        assertThat(recorder.findFileToExtract(cvId)).isEmpty();
        assertThat(cv.getExtractionStatus()).isEqualTo(CvExtractionStatus.EXTRACTED);
        assertThat(cv.getExtractedText()).isEqualTo("Backend engineer");
    }

    @Test
    void storesTheText() {
        stored();

        recorder.recordExtracted(cvId, new ExtractedCvResult("Backend engineer", 16, 1, false));

        assertThat(cv.getExtractionStatus()).isEqualTo(CvExtractionStatus.EXTRACTED);
        assertThat(cv.getExtractedText()).isEqualTo("Backend engineer");
        assertThat(cv.getTextExtractedAt()).isNotNull();
    }

    @Test
    void marksAScan() {
        stored();

        recorder.recordExtracted(cvId, new ExtractedCvResult("", 0, 2, true));

        assertThat(cv.getExtractionStatus()).isEqualTo(CvExtractionStatus.EMPTY_OR_SCANNED);
    }

    @Test
    void marksAnUnreadableFile() {
        stored();

        recorder.recordUnreadable(cvId);

        assertThat(cv.getExtractionStatus()).isEqualTo(CvExtractionStatus.FAILED);
    }

    @Test
    void aLateResult_neverReplacesOneAlreadyRecorded() {
        cv.recordExtraction("first", false, Instant.now());
        stored();

        recorder.recordExtracted(cvId, new ExtractedCvResult("second", 6, 1, false));
        recorder.recordUnreadable(cvId);

        assertThat(cv.getExtractionStatus()).isEqualTo(CvExtractionStatus.EXTRACTED);
        assertThat(cv.getExtractedText()).isEqualTo("first");
    }
}
