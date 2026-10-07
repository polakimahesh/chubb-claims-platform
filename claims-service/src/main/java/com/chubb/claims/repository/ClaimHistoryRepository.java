package com.chubb.claims.repository;

import com.chubb.claims.entity.ClaimHistory;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ClaimHistoryRepository extends JpaRepository<ClaimHistory, UUID> {
    List<ClaimHistory> findByClaimIdOrderByOccurredAtAsc(UUID claimId);
}
