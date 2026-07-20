package com.talentpipe.notification;

import com.talentpipe.notification.entity.Notification;
import com.talentpipe.notification.entity.NotificationType;
import com.talentpipe.notification.repository.NotificationRepository;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Persists notification attempts in their own transactions.
 *
 * <p>Separate from {@link NotificationDispatcher} because the dispatcher runs
 * <em>after</em> the originating transaction has committed, on an async thread
 * — each write here needs a fresh transaction of its own, and a self-invoked
 * {@code @Transactional} method would bypass the proxy that provides it.</p>
 */
@Component
public class NotificationRecorder {

    private final NotificationRepository notificationRepository;

    public NotificationRecorder(NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    /** Records a PENDING attempt and returns its id. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public UUID recordPending(UUID tenantId, UUID userId, NotificationType type, String recipient) {
        Notification notification = notificationRepository.save(
                new Notification(tenantId, userId, type, recipient));
        return notification.getId();
    }

    /** Moves an attempt to SENT. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markSent(UUID notificationId) {
        notificationRepository.findById(notificationId)
                .ifPresent(notification -> notification.markSent(Instant.now()));
    }

    /** Moves an attempt to FAILED, keeping a truncated reason. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailed(UUID notificationId, String reason) {
        notificationRepository.findById(notificationId)
                .ifPresent(notification -> notification.markFailed(reason));
    }
}
