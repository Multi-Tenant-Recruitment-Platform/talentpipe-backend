package com.talentpipe.notification;

import static org.assertj.core.api.Assertions.assertThat;

import com.talentpipe.notification.entity.NotificationType;
import com.talentpipe.notification.event.NotificationRequestedEvent;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class EmailTemplatesTest {

    @ParameterizedTest
    @EnumSource(NotificationType.class)
    void rendersEveryNotificationTypeWithEscapedRecipientData(NotificationType type) {
        NotificationRequestedEvent event = NotificationRequestedEvent.forCandidate(
                type, "user@example.com", "https://example.test/?q=1&x=2", "<Admin>");

        EmailMessage message = EmailTemplates.render(event);

        assertThat(message.to()).isEqualTo("user@example.com");
        assertThat(message.html()).contains("&lt;Admin&gt;").doesNotContain("<Admin>");
    }
}
