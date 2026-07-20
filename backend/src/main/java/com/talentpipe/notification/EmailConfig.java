package com.talentpipe.notification;

import java.time.Duration;
import java.util.concurrent.Executor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Wiring for the notification module: transport selection and the executor
 * that carries email off the request thread.
 */
@Configuration
@EnableAsync
public class EmailConfig {

    private static final Logger log = LoggerFactory.getLogger(EmailConfig.class);

    /** Executor name referenced by {@code @Async} on the dispatcher. */
    public static final String EMAIL_EXECUTOR = "emailExecutor";

    /**
     * Selects the transport from configuration. With a key present, mail is
     * really sent; without one the app degrades to console output instead of
     * failing to start — a developer clone with no secrets must still boot and
     * complete every flow.
     */
    @Bean
    public EmailService emailService(ResendProperties properties, RestTemplateBuilder builder) {
        if (!properties.isConfigured()) {
            log.warn("RESEND_API_KEY is not set - running in CONSOLE email mode. "
                    + "Verification, reset and invitation links will be written to this log "
                    + "and no mail will be delivered.");
            return new ConsoleEmailService();
        }
        // Finite timeouts: a hung mail API must not occupy a pool thread forever.
        var restTemplate = builder
                .connectTimeout(Duration.ofSeconds(5))
                .readTimeout(Duration.ofSeconds(10))
                .build();
        log.info("Resend email transport active (from={})", properties.from());
        return new ResendEmailService(restTemplate, properties.apiKey(), properties.from());
    }

    /**
     * Bounded pool for outbound email. Bounded on purpose: unlike the raw
     * {@code new Thread()} it replaces, a burst of registrations can no longer
     * spawn unlimited threads. When the queue saturates, the caller runs the
     * task itself — the send is slow rather than silently dropped.
     */
    @Bean(name = EMAIL_EXECUTOR)
    public Executor emailExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(5);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("email-");
        executor.setRejectedExecutionHandler(
                new java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }
}
