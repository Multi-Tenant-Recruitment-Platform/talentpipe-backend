package com.talentpipe.candidate.extraction;

import com.talentpipe.ai.ExtractedCvResult;
import com.talentpipe.candidate.entity.CandidateCv;
import com.talentpipe.candidate.entity.CvExtractionStatus;
import com.talentpipe.candidate.repository.CandidateCvRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * The database side of CV extraction, each step in a short transaction of its own.
 *
 * <p>Separate from {@link CvExtractionDispatcher} so that no transaction is open
 * while the AI service is working, and because a self-invoked
 * {@code @Transactional} method would bypass the proxy (as with
 * {@code NotificationRecorder}).</p>
 *
 * <p>Only a PENDING CV is ever changed, so a result that arrives twice, or after
 * something else recorded one, is ignored.</p>
 */
@Component
public class CvExtractionRecorder {

    private static final List<CvExtractionStatus> READ =
            List.of(CvExtractionStatus.EXTRACTED, CvExtractionStatus.EMPTY_OR_SCANNED);

    private final CandidateCvRepository cvRepository;

    public CvExtractionRecorder(CandidateCvRepository cvRepository) {
        this.cvRepository = cvRepository;
    }

    /** What the extractor needs to know about a stored CV. */
    public record CvFile(String storageKey, String fileName, String mimeType) {
    }

    /**
     * The file to extract for this CV, or empty when there is nothing left to do:
     * it is gone, or has a result already, or takes over the result of an
     * identical file — the same bytes always give the same text.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Optional<CvFile> findFileToExtract(UUID cvId) {
        Optional<CandidateCv> pending = findPending(cvId);
        if (pending.isEmpty()) {
            return Optional.empty();
        }
        CandidateCv cv = pending.get();
        Optional<CandidateCv> identical =
                cvRepository.findFirstByFileHashSha256AndExtractionStatusIn(cv.getFileHashSha256(), READ);
        if (identical.isPresent()) {
            cv.copyExtractionFrom(identical.get());
            return Optional.empty();
        }
        return Optional.of(new CvFile(cv.getStorageKey(), cv.getFileName(), cv.getMimeType()));
    }

    /** Stores what was found in the CV. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordExtracted(UUID cvId, ExtractedCvResult result) {
        findPending(cvId).ifPresent(cv ->
                cv.recordExtraction(result.text(), result.emptyOrScanned(), Instant.now()));
    }

    /** Marks the CV as one that cannot be read. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordUnreadable(UUID cvId) {
        findPending(cvId).ifPresent(CandidateCv::recordExtractionFailure);
    }

    private Optional<CandidateCv> findPending(UUID cvId) {
        return cvRepository.findById(cvId)
                .filter(cv -> cv.getExtractionStatus() == CvExtractionStatus.PENDING);
    }
}
