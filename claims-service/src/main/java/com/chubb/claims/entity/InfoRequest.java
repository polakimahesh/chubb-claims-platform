package com.chubb.claims.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "info_requests")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class InfoRequest {
    @Id
    private UUID id;
    private UUID claimId;
    private String question;
    private String requestedBy;
    private Instant requestedAt;
    private String response;
    private Instant respondedAt;

    public InfoRequest(UUID claimId, String question, String requestedBy, Instant now) {
        this.id = UUID.randomUUID();
        this.claimId = claimId;
        this.question = question;
        this.requestedBy = requestedBy;
        this.requestedAt = now;
    }

    public boolean isOpen() {
        return response == null;
    }

    public void respond(String response, Instant now) {
        this.response = response;
        this.respondedAt = now;
    }
}
