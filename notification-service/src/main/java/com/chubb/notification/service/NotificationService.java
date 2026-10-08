package com.chubb.notification.service;

import com.chubb.claims.events.ClaimEvent;
import com.chubb.notification.dto.NotificationResponse;
import com.chubb.notification.entity.Notification;
import com.chubb.notification.repository.NotificationRepository;
import java.time.Clock;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notifications;
    private final NotificationTemplates templates;
    private final NotificationSender sender;
    private final Clock clock;

    /**
     * Notifies the claimant about an event. Idempotent: the unique event id means a redelivered event is skipped.
     * The row is written before sending and the whole step is one transaction, so a failed send rolls back and the
     * event is retried (then dead-lettered) rather than silently lost.
     *
     * @return true if a notification was sent
     */
    @Transactional
    public boolean handle(ClaimEvent event) {
        Optional<NotificationTemplates.Message> message = templates.messageFor(event);
        if (message.isEmpty() || event.claimantEmail() == null) {
            return false;
        }
        if (notifications.existsByEventId(event.eventId())) {
            log.debug("Event {} already notified, skipping", event.eventId());
            return false;
        }
        try {
            notifications.saveAndFlush(new Notification(event.eventId(), event.claimId(), event.claimNumber(),
                    event.claimantEmail(), event.type(), sender.channel(), message.get().subject(),
                    message.get().body(), clock.instant()));
        } catch (DataIntegrityViolationException duplicate) {
            return false;   // concurrent delivery of the same event
        }
        sender.send(event.claimantEmail(), message.get().subject(), message.get().body());
        return true;
    }

    @Transactional(readOnly = true)
    public Page<NotificationResponse> forRecipient(String email, int page, int size) {
        return notifications.findByRecipientEmailIgnoreCase(email, pageable(page, size))
                .map(NotificationResponse::from);
    }

    @Transactional(readOnly = true)
    public Page<NotificationResponse> all(int page, int size) {
        return notifications.findAll(pageable(page, size)).map(NotificationResponse::from);
    }

    private static PageRequest pageable(int page, int size) {
        return PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100), Sort.by("createdAt").descending());
    }
}
