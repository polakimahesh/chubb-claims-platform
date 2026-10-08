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
@Table(name = "claim_documents")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ClaimDocument {
    @Id
    private UUID id;
    private UUID claimId;
    private String fileName;
    private String contentType;
    private long sizeBytes;
    private String storageKey;
    private String uploadedBy;
    private Instant uploadedAt;

    public ClaimDocument(UUID claimId, String fileName, String contentType, long sizeBytes, String storageKey,
                         String uploadedBy, Instant now) {
        this.id = UUID.randomUUID();
        this.claimId = claimId;
        this.fileName = fileName;
        this.contentType = contentType;
        this.sizeBytes = sizeBytes;
        this.storageKey = storageKey;
        this.uploadedBy = uploadedBy;
        this.uploadedAt = now;
    }
}
