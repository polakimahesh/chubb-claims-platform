package com.chubb.claims.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "outbox_events")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OutboxEvent {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private UUID aggregateId;
    @Column(columnDefinition = "TEXT")
    private String payload;
    private Instant createdAt;
    private Instant publishedAt;

    public OutboxEvent(UUID aggregateId, String payload, Instant now) {
        this.aggregateId = aggregateId;
        this.payload = payload;
        this.createdAt = now;
    }

    public void markPublished(Instant now) {
        this.publishedAt = now;
    }
}
