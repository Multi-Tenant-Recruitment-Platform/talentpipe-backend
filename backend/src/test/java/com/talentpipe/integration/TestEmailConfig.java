package com.talentpipe.integration;

import com.talentpipe.notification.EmailService;
import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * Test-only Spring configuration that replaces {@link com.talentpipe.notification.ConsoleEmailService}
 * with an in-memory implementation, allowing integration tests to capture sent
 * emails and extract token links without parsing log output.
 */
@TestConfiguration
public class TestEmailConfig {

    @Bean
    @Primary
    public CapturingEmailService capturingEmailService() {
        return new CapturingEmailService();
    }

    /**
     * In-memory email service that records every sent message.
     * Tests call {@link #lastVerificationLink()} / {@link #lastResetLink()} to
     * retrieve the most recent emailed link and extract the raw token from it.
     */
    public static class CapturingEmailService implements EmailService {

        private final List<String> verificationLinks = new ArrayList<>();
        private final List<String> resetLinks = new ArrayList<>();

        @Override
        public void sendVerificationEmail(String to, String verificationLink) {
            verificationLinks.add(verificationLink);
        }

        @Override
        public void sendPasswordResetEmail(String to, String resetLink) {
            resetLinks.add(resetLink);
        }

        public String lastVerificationLink() {
            if (verificationLinks.isEmpty()) {
                throw new IllegalStateException("No verification email has been sent");
            }
            return verificationLinks.get(verificationLinks.size() - 1);
        }

        public String lastResetLink() {
            if (resetLinks.isEmpty()) {
                throw new IllegalStateException("No reset email has been sent");
            }
            return resetLinks.get(resetLinks.size() - 1);
        }

        public void clear() {
            verificationLinks.clear();
            resetLinks.clear();
        }
    }
}
