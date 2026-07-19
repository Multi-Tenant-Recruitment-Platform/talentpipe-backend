package com.talentpipe.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import java.util.Map;

/**
 * Production email sender using the Resend REST API.
 *
 * <p>Annotated {@code @Primary} to take precedence over {@code ConsoleEmailService}.
 * Out-of-the-box configures the developer's provided key and handles asynchronous
 * fire-and-forget operations gracefully.</p>
 */
@Primary
@Service
public class ResendEmailService implements EmailService {

    private static final Logger log = LoggerFactory.getLogger(ResendEmailService.class);

    private final RestTemplate restTemplate;
    private final String apiKey;
    private final String fromEmail;

    public ResendEmailService(
            @Value("${talentpipe.resend.api-key:re_P7LBeJ1E_9WPE3QWVsHQD3hGDaPU5YVkS}") String apiKey,
            @Value("${talentpipe.resend.from:onboarding@resend.dev}") String fromEmail) {
        this.restTemplate = new RestTemplate();
        this.apiKey = apiKey;
        this.fromEmail = fromEmail;
    }

    @Override
    public void sendVerificationEmail(String to, String verificationLink) {
        // Fallback: log to console first so developers are never blocked by Resend sandbox limits.
        log.info("[EMAIL-FALLBACK] Verification link for {}: {}", to, verificationLink);

        String subject = "Verify your TalentPipe account";
        String htmlContent = String.format(
                "<p>Welcome to TalentPipe!</p>" +
                "<p>Please verify your email address by clicking the link below:</p>" +
                "<p><a href=\"%s\">%s</a></p>" +
                "<p>If you did not request this, please ignore this email.</p>",
                verificationLink, verificationLink);

        sendEmail(to, subject, htmlContent);
    }

    @Override
    public void sendPasswordResetEmail(String to, String resetLink) {
        // Fallback: log to console first.
        log.info("[EMAIL-FALLBACK] Password reset link for {}: {}", to, resetLink);

        String subject = "Reset your TalentPipe password";
        String htmlContent = String.format(
                "<p>You requested a password reset for your TalentPipe account.</p>" +
                "<p>Please click the link below to reset your password (expires in 30 minutes):</p>" +
                "<p><a href=\"%s\">%s</a></p>" +
                "<p>If you did not request this, you can safely ignore this email.</p>",
                resetLink, resetLink);

        sendEmail(to, subject, htmlContent);
    }

    private void sendEmail(String to, String subject, String htmlContent) {
        // Fire-and-forget: dispatch in a background thread so the client request
        // thread is not blocked by Resend HTTP network latency.
        new Thread(() -> {
            try {
                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);
                headers.set("Authorization", "Bearer " + apiKey);

                Map<String, Object> body = Map.of(
                        "from", fromEmail,
                        "to", to,
                        "subject", subject,
                        "html", htmlContent
                );

                HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
                ResponseEntity<String> response = restTemplate.postForEntity(
                        "https://api.resend.com/emails", request, String.class);

                if (response.getStatusCode().is2xxSuccessful()) {
                    log.info("Email successfully sent to {} via Resend. Subject: '{}'", to, subject);
                } else {
                    log.error("Failed to send email via Resend. Status code: {}, Response: {}",
                            response.getStatusCode(), response.getBody());
                }
            } catch (Exception ex) {
                log.error("Exception occurred while sending email via Resend to {}", to, ex);
            }
        }).start();
    }
}
