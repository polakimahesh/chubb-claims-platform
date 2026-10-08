package com.chubb.notification.entity;

import com.chubb.claims.events.ClaimEventType;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "notifications")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Notification {
    @Id
    private UUID id;
    private UUID eventId;
    private UUID claimId;
    private String claimNumber;
    private String recipientEmail;
    @Enumerated(EnumType.STRING)
    private ClaimEventType eventType;
    private String channel;
    private String subject;
    private String body;
    private Instant createdAt;

    public Notification(UUID eventId, UUID claimId, String claimNumber, String recipientEmail,
                        ClaimEventType eventType, String channel, String subject, String body, Instant now) {
        this.id = UUID.randomUUID();
        this.eventId = eventId;
        this.claimId = claimId;
        this.claimNumber = claimNumber;
        this.recipientEmail = recipientEmail;
        this.eventType = eventType;
        this.channel = channel;
        this.subject = subject;
        this.body = body;
        this.createdAt = now;
    }
}
