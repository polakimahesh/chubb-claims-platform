package com.chubb.reporting.service;

import com.chubb.claims.events.ClaimStatus;
import com.chubb.claims.events.ClaimType;
import com.chubb.claims.events.Market;
import com.chubb.reporting.dto.ReportResponses.BreachType;
import com.chubb.reporting.dto.ReportResponses.ConvertedExposure;
import com.chubb.reporting.dto.ReportResponses.Exposure;
import com.chubb.reporting.dto.ReportResponses.ExposureTotal;
import com.chubb.reporting.dto.ReportResponses.OfficerPerformance;
import com.chubb.reporting.dto.ReportResponses.OfficerWorkload;
import com.chubb.reporting.dto.ReportResponses.SlaBreach;
import com.chubb.reporting.dto.ReportResponses.Summary;
import com.chubb.reporting.entity.ClaimView;
import com.chubb.reporting.exception.UnsupportedCurrencyException;
import com.chubb.reporting.fx.FxRateProvider;
import com.chubb.reporting.repository.ClaimViewRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Manager-facing reports. Every aggregation runs in the database; Java only reshapes the grouped rows. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReportService {

    private static final int MAX_SLA_ROWS = 500;

    private final ClaimViewRepository views;
    private final FxRateProvider fx;
    private final Clock clock;

    @Value("${reporting.sla.unassigned-hours:24}")
    private long unassignedHours;
    @Value("${reporting.sla.open-days:7}")
    private long openDays;

    public List<Exposure> exposure(Market market) {
        return views.openExposure(market).stream()
                .map(r -> new Exposure((Market) r[0], (ClaimType) r[1], (String) r[2], (Long) r[3], (BigDecimal) r[4]))
                .toList();
    }

    /** Total open exposure expressed in one currency (static indicative rates, see {@link FxRateProvider#source()}). */
    public ExposureTotal exposureTotal(String baseCurrency) {
        String base = baseCurrency.toUpperCase();
        if (!fx.supportedCurrencies().contains(base)) {
            throw new UnsupportedCurrencyException(baseCurrency);
        }
        Map<String, long[]> counts = new TreeMap<>();
        Map<String, BigDecimal> amounts = new TreeMap<>();
        for (Exposure e : exposure(null)) {
            counts.computeIfAbsent(e.currency(), c -> new long[1])[0] += e.openClaims();
            amounts.merge(e.currency(), e.totalExposure(), BigDecimal::add);
        }
        List<ConvertedExposure> breakdown = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        long open = 0;
        for (Map.Entry<String, BigDecimal> entry : amounts.entrySet()) {
            BigDecimal converted = fx.convert(entry.getValue(), entry.getKey(), base);
            long n = counts.get(entry.getKey())[0];
            breakdown.add(new ConvertedExposure(entry.getKey(), n, entry.getValue(), converted));
            total = total.add(converted);
            open += n;
        }
        return new ExposureTotal(base, total, open, breakdown, fx.source());
    }

    public List<OfficerWorkload> workload() {
        Map<String, Map<ClaimStatus, Long>> byOfficer = new TreeMap<>();
        for (Object[] r : views.openWorkload()) {
            byOfficer.computeIfAbsent((String) r[0], k -> new EnumMap<>(ClaimStatus.class))
                    .put((ClaimStatus) r[1], (Long) r[2]);
        }
        return byOfficer.entrySet().stream()
                .map(e -> new OfficerWorkload(e.getKey(), e.getValue().values().stream().mapToLong(Long::longValue).sum(),
                        e.getValue()))
                .toList();
    }

    /** Closed-claim counts and average resolution time per officer, aggregated in SQL. */
    public List<OfficerPerformance> performance() {
        record Acc(long settled, long rejected, double secondsTotal, long timed) {
        }
        Map<String, Acc> byOfficer = new TreeMap<>();
        for (Object[] r : views.closedPerformance()) {
            String officer = (String) r[0];
            ClaimStatus status = (ClaimStatus) r[1];
            long count = (Long) r[2];
            double avgSeconds = r[3] == null ? 0 : ((Number) r[3]).doubleValue();
            Acc prev = byOfficer.getOrDefault(officer, new Acc(0, 0, 0, 0));
            byOfficer.put(officer, new Acc(prev.settled + (status == ClaimStatus.SETTLED ? count : 0),
                    prev.rejected + (status == ClaimStatus.REJECTED ? count : 0),
                    prev.secondsTotal + avgSeconds * count, prev.timed + count));
        }
        return byOfficer.entrySet().stream().map(e -> {
            Acc a = e.getValue();
            double avgHours = a.timed == 0 ? 0 : a.secondsTotal / a.timed / 3600.0;
            return new OfficerPerformance(e.getKey(), a.settled + a.rejected, a.settled, a.rejected,
                    Math.round(avgHours * 100) / 100.0);
        }).toList();
    }

    /**
     * Claims over their service level: unassigned too long (SUBMITTED beyond {@code reporting.sla.unassigned-hours})
     * and still open too long (UNDER_REVIEW / APPROVED beyond {@code reporting.sla.open-days}). Claims waiting on the
     * claimant (INFO_REQUESTED) are not counted against the officer. Oldest first, capped at {@value MAX_SLA_ROWS}.
     */
    public List<SlaBreach> slaBreaches() {
        Instant now = clock.instant();
        List<SlaBreach> result = new ArrayList<>();
        Duration unassignedLimit = Duration.ofHours(unassignedHours);
        Duration openLimit = Duration.ofDays(openDays);
        views.findOlderThan(EnumSet.of(ClaimStatus.SUBMITTED), now.minus(unassignedLimit), PageRequest.of(0, MAX_SLA_ROWS))
                .forEach(c -> result.add(breach(c, BreachType.UNASSIGNED_TOO_LONG, now, unassignedLimit)));
        views.findOlderThan(EnumSet.of(ClaimStatus.UNDER_REVIEW, ClaimStatus.APPROVED), now.minus(openLimit),
                        PageRequest.of(0, MAX_SLA_ROWS))
                .forEach(c -> result.add(breach(c, BreachType.OPEN_TOO_LONG, now, openLimit)));
        result.sort((a, b) -> Long.compare(b.ageHours(), a.ageHours()));
        return result.size() > MAX_SLA_ROWS ? result.subList(0, MAX_SLA_ROWS) : result;
    }

    public Summary summary() {
        Map<ClaimStatus, Long> byStatus = new EnumMap<>(ClaimStatus.class);
        for (ClaimStatus s : ClaimStatus.values()) {
            byStatus.put(s, 0L);
        }
        views.countByStatus().forEach(r -> byStatus.put((ClaimStatus) r[0], (Long) r[1]));
        long open = byStatus.entrySet().stream().filter(e -> !e.getKey().isTerminal())
                .mapToLong(Map.Entry::getValue).sum();
        return new Summary(byStatus, open, byStatus.get(ClaimStatus.SUBMITTED), exposure(null));
    }

    private static SlaBreach breach(ClaimView c, BreachType type, Instant now, Duration threshold) {
        return new SlaBreach(c.getClaimId(), c.getClaimNumber(), c.getMarket(), c.getClaimType(), c.getStatus(),
                c.getAssignedOfficerId(), type, Duration.between(c.getSubmittedAt(), now).toHours(),
                threshold.toHours());
    }
}
