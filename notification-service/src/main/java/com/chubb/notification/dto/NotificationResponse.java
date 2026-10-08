package com.chubb.notification.dto;

import com.chubb.claims.events.ClaimEventType;
import com.chubb.notification.entity.Notification;
import java.time.Instant;
import java.util.UUID;

public record NotificationResponse(UUID id, UUID claimId, String claimNumber, String recipientEmail,
                                   ClaimEventType eventType, String channel, String subject, String body,
                                   Instant createdAt) {
    public static NotificationResponse from(Notification n) {
        return new NotificationResponse(n.getId(), n.getClaimId(), n.getClaimNumber(), n.getRecipientEmail(),
                n.getEventType(), n.getChannel(), n.getSubject(), n.getBody(), n.getCreatedAt());
    }
}
