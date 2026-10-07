package com.chubb.claims.repository;

import com.chubb.claims.entity.InfoRequest;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InfoRequestRepository extends JpaRepository<InfoRequest, UUID> {
    List<InfoRequest> findByClaimIdOrderByRequestedAtAsc(UUID claimId);

    boolean existsByClaimIdAndResponseIsNull(UUID claimId);
}
