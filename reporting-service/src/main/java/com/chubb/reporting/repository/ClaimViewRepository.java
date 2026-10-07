package com.chubb.reporting.repository;

import com.chubb.claims.events.ClaimStatus;
import com.chubb.claims.events.Market;
import com.chubb.reporting.entity.ClaimView;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ClaimViewRepository extends JpaRepository<ClaimView, UUID> {

    /** Rows: market, claimType, currency, count, exposure. Open = not REJECTED/SETTLED. */
    @Query("""
            select c.market, c.claimType, c.currency, count(c), sum(coalesce(c.assessedAmount, c.estimatedAmount))
            from ClaimView c
            where c.status not in (com.chubb.claims.events.ClaimStatus.REJECTED, com.chubb.claims.events.ClaimStatus.SETTLED)
              and (:market is null or c.market = :market)
            group by c.market, c.claimType, c.currency
            order by c.market, c.claimType, c.currency
            """)
    List<Object[]> openExposure(@Param("market") Market market);

    /** Rows: officerId, status, count for open claims that have been picked up. */
    @Query("""
            select c.assignedOfficerId, c.status, count(c)
            from ClaimView c
            where c.assignedOfficerId is not null
              and c.status not in (com.chubb.claims.events.ClaimStatus.REJECTED, com.chubb.claims.events.ClaimStatus.SETTLED)
            group by c.assignedOfficerId, c.status
            """)
    List<Object[]> openWorkload();

    /** Rows: status, count over all claims. */
    @Query("select c.status, count(c) from ClaimView c group by c.status")
    List<Object[]> countByStatus();

    List<ClaimView> findByStatusInAndAssignedOfficerIdIsNotNull(Collection<ClaimStatus> statuses);
}
