package com.talentpipe.notification;

/**
 * Email dispatch contract for TalentPipe. Implementations are swapped per
 * environment: {@link ConsoleEmailService} logs links to the console during
 * Sprint 1; a real SMTP implementation arrives when mail infrastructure is
 * set up.
 *
 * <p>All methods are fire-and-forget: callers do not block on delivery. Any
 * sending failure should be caught and logged by the implementation so a
 * transient mail error never rolls back a business transaction.</p>
 */
public interface EmailService {

    /**
     * Sends an account activation email containing {@code verificationLink}.
     *
     * @param to               recipient email address
     * @param verificationLink the full URL the user must click to verify their account
     */
    void sendVerificationEmail(String to, String verificationLink);

    /**
     * Sends a password reset email containing {@code resetLink}.
     *
     * @param to        recipient email address
     * @param resetLink the full URL (with embedded token) for resetting the password
     */
    void sendPasswordResetEmail(String to, String resetLink);
}
