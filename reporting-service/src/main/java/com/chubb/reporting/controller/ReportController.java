package com.chubb.reporting.controller;

import com.chubb.claims.events.Market;
import com.chubb.reporting.dto.ReportResponses.Exposure;
import com.chubb.reporting.dto.ReportResponses.OfficerPerformance;
import com.chubb.reporting.dto.ReportResponses.OfficerWorkload;
import com.chubb.reporting.dto.ReportResponses.Summary;
import com.chubb.reporting.service.ReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Manager-facing read API. Eventually consistent with claims-service (Kafka lag). */
@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
@Tag(name = "Reports")
public class ReportController {

    private final ReportService reports;

    @GetMapping("/summary")
    @Operation(summary = "Claims by status, backlog and outstanding exposure")
    public Summary summary() {
        return reports.summary();
    }

    @GetMapping("/exposure")
    @Operation(summary = "Outstanding liability exposure of open claims by market, type and currency")
    public List<Exposure> exposure(@RequestParam(required = false) Market market) {
        return reports.exposure(market);
    }

    @GetMapping("/workload")
    @Operation(summary = "Open claims per officer, by status")
    public List<OfficerWorkload> workload() {
        return reports.workload();
    }

    @GetMapping("/performance")
    @Operation(summary = "Closed claims and average resolution time per officer")
    public List<OfficerPerformance> performance() {
        return reports.performance();
    }
}
