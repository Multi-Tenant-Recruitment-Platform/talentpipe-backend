package com.talentpipe.notification;

/**
 * A rendered outbound email, ready for a transport to deliver.
 *
 * @param to      recipient address
 * @param subject subject line
 * @param html    HTML body (Resend derives the plain-text part automatically)
 */
public record EmailMessage(String to, String subject, String html) {
}
