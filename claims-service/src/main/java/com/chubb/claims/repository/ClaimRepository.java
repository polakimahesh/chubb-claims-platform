package com.chubb.claims.repository;

import com.chubb.claims.entity.Claim;
import com.chubb.claims.events.ClaimStatus;
import com.chubb.claims.events.ClaimType;
import com.chubb.claims.events.Market;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ClaimRepository extends JpaRepository<Claim, UUID> {

    Page<Claim> findByClaimantEmailIgnoreCase(String email, Pageable pageable);

    /** Intake queue: unassigned SUBMITTED claims, optionally narrowed by market and type. */
    @Query("""
            select c from Claim c
            where c.status = com.chubb.claims.events.ClaimStatus.SUBMITTED
              and (:market is null or c.market = :market)
              and (:type is null or c.claimType = :type)
            """)
    Page<Claim> findQueue(@Param("market") Market market, @Param("type") ClaimType type, Pageable pageable);

    /** An officer's workload, optionally filtered by status. */
    @Query("""
            select c from Claim c
            where c.assignedOfficerId = :officerId
              and (:status is null or c.status = :status)
            """)
    Page<Claim> findByOfficer(@Param("officerId") String officerId, @Param("status") ClaimStatus status,
                              Pageable pageable);
}
