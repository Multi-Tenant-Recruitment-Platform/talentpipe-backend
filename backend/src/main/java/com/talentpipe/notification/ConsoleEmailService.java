package com.talentpipe.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

/**
 * Sprint 1 email implementation: prints email content to the console log.
 *
 * <p>No SMTP infrastructure is required. The verification and reset links are
 * logged at INFO level (prefixed with {@code [EMAIL-CONSOLE]}) so developers
 * can copy them directly from the backend log during manual testing.</p>
 *
 * <p>Annotated {@code @Primary} so this bean wins automatically when a real
 * SMTP implementation is also on the classpath (e.g. in a future sprint)
 * without requiring any code changes to callers.</p>
 */
@Primary
@Service
public class ConsoleEmailService implements EmailService {

    private static final Logger log = LoggerFactory.getLogger(ConsoleEmailService.class);

    @Override
    public void sendVerificationEmail(String to, String verificationLink) {
        log.info("""
                [EMAIL-CONSOLE] ── Account Verification ────────────────────────────
                  To      : {}
                  Subject : Verify your TalentPipe account
                  Link    : {}
                ─────────────────────────────────────────────────────────────────────""",
                to, verificationLink);
    }

    @Override
    public void sendPasswordResetEmail(String to, String resetLink) {
        log.info("""
                [EMAIL-CONSOLE] ── Password Reset ───────────────────────────────────
                  To      : {}
                  Subject : Reset your TalentPipe password
                  Link    : {}  (expires in 30 minutes)
                ─────────────────────────────────────────────────────────────────────""",
                to, resetLink);
    }
}
