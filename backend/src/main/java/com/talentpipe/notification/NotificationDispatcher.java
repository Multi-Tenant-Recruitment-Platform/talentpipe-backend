package com.talentpipe.notification;

import com.talentpipe.notification.event.NotificationRequestedEvent;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * The single place where email actually leaves the system.
 *
 * <p>Two annotations carry the whole design:</p>
 * <ul>
 *   <li>{@code @TransactionalEventListener} (default phase AFTER_COMMIT) — mail
 *       goes out only once the originating transaction has committed. No email
 *       is ever sent for a registration that rolled back, and the token row the
 *       link points at is guaranteed to exist by the time it is delivered.</li>
 *   <li>{@code @Async} — delivery runs on the bounded email pool, so the HTTP
 *       request thread returns immediately. Resend latency never becomes API
 *       latency.</li>
 * </ul>
 *
 * <p>Nothing thrown here can reach the HTTP caller (different thread, after
 * commit). That is the point: a mail outage marks the attempt FAILED and is
 * logged with a correlation id, while registration, password reset and
 * invitation all still succeed. Users recover through the resend endpoints.</p>
 */
@Component
public class NotificationDispatcher {

    private static final Logger log = LoggerFactory.getLogger(NotificationDispatcher.class);

    private final EmailService emailService;
    private final NotificationRecorder recorder;

    public NotificationDispatcher(EmailService emailService, NotificationRecorder recorder) {
        this.emailService = emailService;
        this.recorder = recorder;
    }

    /**
     * @param event  what to send and to whom. {@code fallbackExecution = true}
     *               so events published outside a transaction (e.g. a resend
     *               triggered from a read-only path) are still delivered.
     */
    @Async(EmailConfig.EMAIL_EXECUTOR)
    @TransactionalEventListener(fallbackExecution = true)
    public void onNotificationRequested(NotificationRequestedEvent event) {
        // Company-user mail is auditable; candidate mail is not recordable in
        // the tenant-scoped notifications table (see the event's javadoc).
        UUID notificationId = event.isRecordable()
                ? recorder.recordPending(event.tenantId(), event.userId(), event.type(), event.recipient())
                : null;

        try {
            emailService.send(EmailTemplates.render(event));
            if (notificationId != null) {
                recorder.markSent(notificationId);
            }
        } catch (Exception ex) {
            // Correlation id ties this failure to the operations record without
            // ever logging the link, which carries a live single-use token.
            String correlationId = UUID.randomUUID().toString();
            log.error("Email delivery FAILED [correlationId={}] type={} notificationId={} recipient={}",
                    correlationId, event.type(), notificationId, event.recipient(), ex);
            if (notificationId != null) {
                recorder.markFailed(notificationId, "[" + correlationId + "] " + ex.getMessage());
            }
        }
    }
}
