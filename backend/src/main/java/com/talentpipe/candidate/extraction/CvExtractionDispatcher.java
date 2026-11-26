package com.talentpipe.candidate.extraction;

import com.talentpipe.ai.CvTextExtractor;
import com.talentpipe.ai.ExtractedCvResult;
import com.talentpipe.ai.UnreadableCvException;
import com.talentpipe.candidate.event.CvUploadedEvent;
import com.talentpipe.candidate.extraction.CvExtractionRecorder.CvFile;
import com.talentpipe.common.storage.FileStorageService;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Extracts the text of a CV as soon as it has been uploaded (ADR-15).
 *
 * <p>Built like {@code NotificationDispatcher} (ADR-6): after the upload has
 * committed, and on its own threads. An AI service that is down, slow or not
 * deployed can therefore never fail or delay an upload — the CV stays PENDING,
 * and screening extracts it when it needs it.</p>
 *
 * <p>No transaction is open while the file is fetched or extracted; the database
 * is only touched before and after, in {@link CvExtractionRecorder}'s short
 * transactions.</p>
 *
 * <p>Never logs the text, the file name or the candidate — only the CV's id.</p>
 */
@Component
public class CvExtractionDispatcher {

    private static final Logger log = LoggerFactory.getLogger(CvExtractionDispatcher.class);

    private final ObjectProvider<CvTextExtractor> extractorProvider;
    private final FileStorageService storageService;
    private final CvExtractionRecorder recorder;

    public CvExtractionDispatcher(ObjectProvider<CvTextExtractor> extractorProvider,
                                  FileStorageService storageService,
                                  CvExtractionRecorder recorder) {
        this.extractorProvider = extractorProvider;
        this.storageService = storageService;
        this.recorder = recorder;
    }

    /** Extracts the uploaded CV, unless the same file has been extracted before. */
    @Async(CvExtractionConfig.CV_EXTRACTION_EXECUTOR)
    @TransactionalEventListener(fallbackExecution = true)
    public void onCvUploaded(CvUploadedEvent event) {
        CvTextExtractor extractor = extractorProvider.getIfAvailable();
        if (extractor == null) {
            log.debug("No CV text extractor is configured - CV {} stays PENDING", event.cvId());
            return;
        }
        try {
            Optional<CvFile> file = recorder.findFileToExtract(event.cvId());
            if (file.isPresent()) {
                extract(event.cvId(), file.get(), extractor);
            }
        } catch (RuntimeException ex) {
            // The type only: an HTTP client's message can quote the response body,
            // and a database error the row — both of which are the CV's text.
            log.warn("CV {} could not be extracted now and stays PENDING ({})",
                    event.cvId(), ex.getClass().getSimpleName());
        }
    }

    private void extract(UUID cvId, CvFile file, CvTextExtractor extractor) {
        byte[] bytes = storageService.load(file.storageKey());
        ExtractedCvResult result;
        try {
            result = extractor.extract(bytes, file.fileName(), file.mimeType());
        } catch (UnreadableCvException ex) {
            recorder.recordUnreadable(cvId);
            log.warn("CV {} cannot be read", cvId);
            return;
        }
        if (result == null) {
            log.warn("The extractor returned nothing for CV {}, which stays PENDING", cvId);
            return;
        }
        recorder.recordExtracted(cvId, result);
        log.info("Extracted CV {}: {}", cvId, result);
    }
}
