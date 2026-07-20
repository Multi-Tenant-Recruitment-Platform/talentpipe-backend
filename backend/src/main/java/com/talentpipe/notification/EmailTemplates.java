package com.talentpipe.notification;

import com.talentpipe.notification.entity.NotificationType;
import com.talentpipe.notification.event.NotificationRequestedEvent;

/**
 * Renders {@link EmailMessage}s for each notification type.
 *
 * <p>Every interpolated value is HTML-escaped: names and company names are
 * user-supplied, and a registration form is an attacker-reachable path into
 * these templates.</p>
 */
final class EmailTemplates {

    private EmailTemplates() {
        // static utility
    }

    static EmailMessage render(NotificationRequestedEvent event) {
        return switch (event.type()) {
            case EMAIL_VERIFICATION -> verification(event);
            case PASSWORD_RESET -> passwordReset(event);
            case INVITATION -> invitation(event);
        };
    }

    private static EmailMessage verification(NotificationRequestedEvent event) {
        String html = layout(
                "Confirm your email",
                greeting(event.recipientName())
                        + "<p>Welcome to TalentPipe. Confirm this email address to activate your account.</p>"
                        + button(event.link(), "Verify my email")
                        + "<p style=\"color:#64748b;font-size:13px\">This link expires in 24 hours. "
                        + "If you did not create a TalentPipe account, you can safely ignore this email.</p>");
        return new EmailMessage(event.recipient(), "Verify your TalentPipe account", html);
    }

    private static EmailMessage passwordReset(NotificationRequestedEvent event) {
        String html = layout(
                "Reset your password",
                greeting(event.recipientName())
                        + "<p>We received a request to reset your TalentPipe password.</p>"
                        + button(event.link(), "Choose a new password")
                        + "<p style=\"color:#64748b;font-size:13px\">This link expires in 30 minutes and can be "
                        + "used once. If you did not request a reset, ignore this email - your password "
                        + "stays unchanged.</p>");
        return new EmailMessage(event.recipient(), "Reset your TalentPipe password", html);
    }

    private static EmailMessage invitation(NotificationRequestedEvent event) {
        String html = layout(
                "You have been invited",
                greeting(event.recipientName())
                        + "<p>You have been invited to join a hiring workspace on TalentPipe. "
                        + "Set a password to accept the invitation and activate your account.</p>"
                        + button(event.link(), "Accept invitation")
                        + "<p style=\"color:#64748b;font-size:13px\">This invitation expires in 7 days.</p>");
        return new EmailMessage(event.recipient(), "You have been invited to TalentPipe", html);
    }

    // ------------------------------------------------------------------ util

    private static String greeting(String name) {
        return name == null || name.isBlank()
                ? "<p>Hello,</p>"
                : "<p>Hi " + escape(name) + ",</p>";
    }

    private static String button(String link, String label) {
        String safeLink = escape(link);
        return "<p style=\"margin:28px 0\">"
                + "<a href=\"" + safeLink + "\" "
                + "style=\"background:#4f46e5;color:#ffffff;padding:12px 20px;border-radius:6px;"
                + "text-decoration:none;font-weight:600;display:inline-block\">" + escape(label) + "</a></p>"
                + "<p style=\"color:#64748b;font-size:13px\">Or paste this link into your browser:<br>"
                + "<span style=\"word-break:break-all\">" + safeLink + "</span></p>";
    }

    private static String layout(String heading, String body) {
        return "<div style=\"font-family:system-ui,-apple-system,Segoe UI,sans-serif;"
                + "max-width:520px;margin:0 auto;color:#0f172a;line-height:1.6\">"
                + "<h1 style=\"font-size:20px;margin:0 0 16px\">" + escape(heading) + "</h1>"
                + body
                + "<hr style=\"border:none;border-top:1px solid #e2e8f0;margin:28px 0\">"
                + "<p style=\"color:#94a3b8;font-size:12px\">TalentPipe - multi-tenant recruitment platform</p>"
                + "</div>";
    }

    /** Minimal HTML entity escaping for interpolated, user-supplied values. */
    private static String escape(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}
