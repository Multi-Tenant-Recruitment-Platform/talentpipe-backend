package com.talentpipe.integration;

import com.talentpipe.notification.EmailMessage;
import com.talentpipe.notification.EmailService;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * Test-only Spring configuration that swaps the real email transport for an
 * in-memory one, so integration tests can read the emailed link without
 * parsing log output or contacting Resend.
 */
@TestConfiguration
public class TestEmailConfig {

    @Bean
    @Primary
    public CapturingEmailService capturingEmailService() {
        return new CapturingEmailService();
    }

    /**
     * Records every message instead of sending it.
     *
     * <p>Delivery runs asynchronously after the originating transaction commits
     * (see {@code NotificationDispatcher}), so the accessors wait briefly for
     * the message to land rather than assuming it is already there.</p>
     */
    public static class CapturingEmailService implements EmailService {

        private static final Duration TIMEOUT = Duration.ofSeconds(5);
        private static final Pattern HREF = Pattern.compile("href=\"([^\"]+)\"");

        private final List<EmailMessage> messages = new CopyOnWriteArrayList<>();

        @Override
        public void send(EmailMessage message) {
            messages.add(message);
        }

        /** Most recent verification link, waiting for it if the send is in flight. */
        public String lastVerificationLink() {
            return awaitLastLinkContaining("/verify-email");
        }

        /** Most recent password-reset link, waiting for it if the send is in flight. */
        public String lastResetLink() {
            return awaitLastLinkContaining("/reset-password");
        }

        /** Most recent invitation link, waiting for it if the send is in flight. */
        public String lastInviteLink() {
            return awaitLastLinkContaining("/accept-invite");
        }

        /** All messages captured so far, newest last. */
        public List<EmailMessage> messages() {
            return List.copyOf(messages);
        }

        public void clear() {
            messages.clear();
        }

        private String awaitLastLinkContaining(String pathFragment) {
            Instant deadline = Instant.now().plus(TIMEOUT);
            while (Instant.now().isBefore(deadline)) {
                String link = findLastLink(pathFragment);
                if (link != null) {
                    return link;
                }
                try {
                    Thread.sleep(50);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
            throw new IllegalStateException("No email containing '" + pathFragment
                    + "' was sent within " + TIMEOUT);
        }

        private String findLastLink(String pathFragment) {
            for (int i = messages.size() - 1; i >= 0; i--) {
                Matcher matcher = HREF.matcher(messages.get(i).html());
                while (matcher.find()) {
                    String href = matcher.group(1);
                    if (href.contains(pathFragment)) {
                        return href;
                    }
                }
            }
            return null;
        }
    }
}
