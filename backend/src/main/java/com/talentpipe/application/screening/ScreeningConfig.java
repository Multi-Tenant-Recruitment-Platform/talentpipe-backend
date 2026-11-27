package com.talentpipe.application.screening;

import java.util.concurrent.Executor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/** The pool applications are screened on, so a slow AI service never holds up an HTTP request. */
@Configuration
public class ScreeningConfig {

    private static final Logger log = LoggerFactory.getLogger(ScreeningConfig.class);

    public static final String SCREENING_EXECUTOR = "applicationScreeningExecutor";

    @Bean(name = SCREENING_EXECUTOR)
    public Executor applicationScreeningExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(200);
        executor.setThreadNamePrefix("screening-");
        executor.setRejectedExecutionHandler((task, pool) ->
                log.warn("Screening queue is full - the application stays unscreened"));
        executor.initialize();
        return executor;
    }
}
