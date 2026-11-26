package com.talentpipe.candidate.extraction;

import java.util.concurrent.Executor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/** The executor that carries CV extraction off the request thread. */
@Configuration
public class CvExtractionConfig {

    private static final Logger log = LoggerFactory.getLogger(CvExtractionConfig.class);

    /** Executor name referenced by {@code @Async} on the dispatcher. */
    public static final String CV_EXTRACTION_EXECUTOR = "cvExtractionExecutor";

    /**
     * Bounded pool, separate from the email pool so that a slow AI service can
     * never delay a verification email.
     *
     * <p>When the queue is full the extraction is dropped, not run by the caller
     * as emails are: the caller is the upload's request thread, still holding its
     * database connection at that point, and nothing is lost — the CV stays
     * PENDING in the database and is extracted later.</p>
     */
    @Bean(name = CV_EXTRACTION_EXECUTOR)
    public Executor cvExtractionExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(5);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("cv-extract-");
        executor.setRejectedExecutionHandler((task, pool) ->
                log.warn("CV extraction queue is full - the CV stays PENDING"));
        executor.initialize();
        return executor;
    }
}
