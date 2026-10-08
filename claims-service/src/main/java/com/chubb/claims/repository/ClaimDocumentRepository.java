package com.chubb.claims.repository;

import com.chubb.claims.entity.ClaimDocument;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ClaimDocumentRepository extends JpaRepository<ClaimDocument, UUID> {
    List<ClaimDocument> findByClaimIdOrderByUploadedAtAsc(UUID claimId);

    long countByClaimId(UUID claimId);
}
