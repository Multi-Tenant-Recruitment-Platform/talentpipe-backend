package com.talentpipe.candidate.extraction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.talentpipe.ai.CvTextExtractor;
import com.talentpipe.ai.ExtractedCvResult;
import com.talentpipe.ai.UnreadableCvException;
import com.talentpipe.candidate.event.CvUploadedEvent;
import com.talentpipe.candidate.extraction.CvExtractionRecorder.CvFile;
import com.talentpipe.common.storage.FileStorageService;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.web.client.ResourceAccessException;

@ExtendWith({MockitoExtension.class, OutputCaptureExtension.class})
class CvExtractionDispatcherTest {

    private static final byte[] BYTES = {0x25, 0x50, 0x44, 0x46};
    private static final String CV_TEXT = "Kasun Perera, backend engineer";

    @Mock
    private ObjectProvider<CvTextExtractor> extractorProvider;

    @Mock
    private CvTextExtractor extractor;

    @Mock
    private FileStorageService storageService;

    @Mock
    private CvExtractionRecorder recorder;

    private final UUID cvId = UUID.randomUUID();
    private final CvFile file = new CvFile("candidate/1/cvs/a.pdf", "kasun-perera.pdf", "application/pdf");

    private CvExtractionDispatcher dispatcher;

    @BeforeEach
    void setUp() {
        dispatcher = new CvExtractionDispatcher(extractorProvider, storageService, recorder);
    }

    private void extractorAvailable() {
        when(extractorProvider.getIfAvailable()).thenReturn(extractor);
    }

    private void fileToExtract() {
        extractorAvailable();
        when(recorder.findFileToExtract(cvId)).thenReturn(Optional.of(file));
        when(storageService.load(file.storageKey())).thenReturn(BYTES);
    }

    private void run() {
        dispatcher.onCvUploaded(new CvUploadedEvent(cvId));
    }

    @Test
    void storesWhatTheExtractorFound() {
        fileToExtract();
        ExtractedCvResult result = new ExtractedCvResult(CV_TEXT, 30, 1, false);
        when(extractor.extract(BYTES, "kasun-perera.pdf", "application/pdf")).thenReturn(result);

        run();

        InOrder order = inOrder(recorder, extractor);
        order.verify(recorder).findFileToExtract(cvId);
        order.verify(extractor).extract(BYTES, "kasun-perera.pdf", "application/pdf");
        order.verify(recorder).recordExtracted(cvId, result);
    }

    @Test
    void withoutAnExtractor_theDatabaseIsNotEvenTouched() {
        when(extractorProvider.getIfAvailable()).thenReturn(null);

        run();

        verifyNoInteractions(recorder, storageService);
    }

    @Test
    void whenThereIsNothingToExtract_theFileIsNotFetched() {
        extractorAvailable();
        when(recorder.findFileToExtract(cvId)).thenReturn(Optional.empty());

        run();

        verifyNoInteractions(storageService, extractor);
    }

    @Test
    void aFileThatCannotBeRead_isRecordedAsSuch() {
        fileToExtract();
        when(extractor.extract(any(), any(), any())).thenThrow(new UnreadableCvException("encrypted"));

        run();

        verify(recorder).recordUnreadable(cvId);
        verify(recorder, never()).recordExtracted(any(), any());
    }

    @Test
    void whenTheServiceIsDown_theCvStaysPending() {
        fileToExtract();
        when(extractor.extract(any(), any(), any())).thenThrow(new ResourceAccessException("Connection refused"));

        assertThatCode(this::run).doesNotThrowAnyException();

        verify(recorder, never()).recordUnreadable(any());
        verify(recorder, never()).recordExtracted(any(), any());
    }

    @Test
    void whenTheExtractorAnswersNothing_theCvStaysPending() {
        fileToExtract();
        when(extractor.extract(any(), any(), any())).thenReturn(null);

        assertThatCode(this::run).doesNotThrowAnyException();

        verify(recorder, never()).recordUnreadable(any());
        verify(recorder, never()).recordExtracted(any(), any());
    }

    @Test
    void whenTheStoredFileCannotBeFetched_theCvStaysPending() {
        extractorAvailable();
        when(recorder.findFileToExtract(cvId)).thenReturn(Optional.of(file));
        when(storageService.load(file.storageKey())).thenThrow(new IllegalStateException("S3 is unavailable"));

        assertThatCode(this::run).doesNotThrowAnyException();

        verifyNoInteractions(extractor);
        verify(recorder, never()).recordUnreadable(any());
    }

    @Test
    void neverLogsTheText_theFileName_orAnErrorThatCouldQuoteThem(CapturedOutput output) {
        fileToExtract();
        when(extractor.extract(any(), any(), any()))
                .thenReturn(new ExtractedCvResult(CV_TEXT, 30, 1, false))
                .thenThrow(new IllegalStateException("unexpected reply: " + CV_TEXT))
                .thenThrow(new UnreadableCvException("could not read " + file.fileName()))
                .thenReturn(new ExtractedCvResult(CV_TEXT, 30, 1, false));
        // Stored the first time; the second time refused, with an error quoting the row.
        doNothing()
                .doThrow(new DataAccessResourceFailureException("failing row contains (" + CV_TEXT + ")"))
                .when(recorder).recordExtracted(any(), any());

        run();
        run();
        run();
        run();

        assertThat(output.getAll())
                .contains(cvId.toString())
                .doesNotContain("Kasun")
                .doesNotContain("kasun-perera");
    }
}
