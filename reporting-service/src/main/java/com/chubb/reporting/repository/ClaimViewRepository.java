package com.chubb.reporting.repository;

import com.chubb.claims.events.ClaimStatus;
import com.chubb.claims.events.Market;
import com.chubb.reporting.entity.ClaimView;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
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

    /** Rows: officerId, status, count, average resolution seconds for closed claims. Aggregated in SQL. */
    @Query("""
            select c.assignedOfficerId, c.status, count(c), avg(c.resolutionSeconds)
            from ClaimView c
            where c.assignedOfficerId is not null
              and c.status in (com.chubb.claims.events.ClaimStatus.REJECTED, com.chubb.claims.events.ClaimStatus.SETTLED)
            group by c.assignedOfficerId, c.status
            """)
    List<Object[]> closedPerformance();

    /** Claims in the given statuses submitted before the cut-off, oldest first (SLA breach scan, bounded by page). */
    @Query("select c from ClaimView c where c.status in :statuses and c.submittedAt < :cutoff order by c.submittedAt asc")
    List<ClaimView> findOlderThan(@Param("statuses") Collection<ClaimStatus> statuses,
                                  @Param("cutoff") Instant cutoff, Pageable limit);
}
