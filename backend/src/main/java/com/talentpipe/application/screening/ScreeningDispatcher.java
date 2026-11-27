package com.talentpipe.application.screening;

import com.talentpipe.application.event.ApplicationSubmittedEvent;
import com.talentpipe.application.service.ApplicationScreeningService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Screens each new application once its submission has committed, on its own
 * pool: the candidate's request never waits for the screener, and an
 * application that failed to save is never screened.
 */
@Component
public class ScreeningDispatcher {

    private static final Logger log = LoggerFactory.getLogger(ScreeningDispatcher.class);

    private final ApplicationScreeningService screening;

    public ScreeningDispatcher(ApplicationScreeningService screening) {
        this.screening = screening;
    }

    @Async(ScreeningConfig.SCREENING_EXECUTOR)
    @TransactionalEventListener(fallbackExecution = true)
    public void onSubmitted(ApplicationSubmittedEvent submitted) {
        try {
            screening.screen(submitted.applicationId());
        } catch (RuntimeException ex) {
            log.warn("Application {} was not screened ({})", submitted.applicationId(),
                    ex.getClass().getSimpleName());
        }
    }
}
