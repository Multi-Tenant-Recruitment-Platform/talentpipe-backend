package com.talentpipe.notification.repository;

import com.talentpipe.notification.entity.Notification;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Persistence access for notification attempts — private to the notification module. */
public interface NotificationRepository extends JpaRepository<Notification, UUID> {
}
