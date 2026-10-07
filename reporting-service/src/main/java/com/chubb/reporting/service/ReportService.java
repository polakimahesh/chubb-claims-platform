package com.chubb.reporting.service;

import com.chubb.claims.events.ClaimStatus;
import com.chubb.claims.events.ClaimType;
import com.chubb.claims.events.Market;
import com.chubb.reporting.dto.ReportResponses.Exposure;
import com.chubb.reporting.dto.ReportResponses.OfficerPerformance;
import com.chubb.reporting.dto.ReportResponses.OfficerWorkload;
import com.chubb.reporting.dto.ReportResponses.Summary;
import com.chubb.reporting.entity.ClaimView;
import com.chubb.reporting.repository.ClaimViewRepository;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReportService {

    private final ClaimViewRepository views;

    public List<Exposure> exposure(Market market) {
        return views.openExposure(market).stream()
                .map(r -> new Exposure((Market) r[0], (ClaimType) r[1], (String) r[2], (Long) r[3], (BigDecimal) r[4]))
                .toList();
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

    /** Aggregated in memory over closed claims; fine at take-home scale (see Known Limitations). */
    public List<OfficerPerformance> performance() {
        Map<String, List<ClaimView>> closed = views
                .findByStatusInAndAssignedOfficerIdIsNotNull(EnumSet.of(ClaimStatus.SETTLED, ClaimStatus.REJECTED))
                .stream().collect(Collectors.groupingBy(ClaimView::getAssignedOfficerId, TreeMap::new, Collectors.toList()));
        return closed.entrySet().stream().map(e -> {
            List<ClaimView> claims = e.getValue();
            double avgHours = claims.stream()
                    .filter(c -> c.getClosedAt() != null)
                    .mapToDouble(c -> Duration.between(c.getSubmittedAt(), c.getClosedAt()).toSeconds() / 3600.0)
                    .average().orElse(0);
            return new OfficerPerformance(e.getKey(), claims.size(),
                    claims.stream().filter(c -> c.getStatus() == ClaimStatus.SETTLED).count(),
                    claims.stream().filter(c -> c.getStatus() == ClaimStatus.REJECTED).count(),
                    Math.round(avgHours * 100) / 100.0);
        }).toList();
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
}
