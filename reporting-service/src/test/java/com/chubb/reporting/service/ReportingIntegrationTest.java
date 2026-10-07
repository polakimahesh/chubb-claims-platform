package com.chubb.reporting.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chubb.claims.events.ClaimEvent;
import com.chubb.claims.events.ClaimEventType;
import com.chubb.claims.events.ClaimStatus;
import com.chubb.claims.events.ClaimType;
import com.chubb.claims.events.Market;
import com.chubb.reporting.dto.ReportResponses.Exposure;
import com.chubb.reporting.repository.ClaimViewRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/** Exercises projection and reports against the real schema; the Kafka listener is not started. */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "spring.kafka.listener.auto-startup=false")
class ReportingIntegrationTest {

    private static final Instant T0 = Instant.parse("2026-01-01T00:00:00Z");

    @Autowired ClaimProjectionService projection;
    @Autowired ReportService reports;
    @Autowired ClaimViewRepository views;
    @Autowired ObjectMapper json;
    @Autowired MockMvc mvc;

    @BeforeEach
    void clean() {
        views.deleteAll();
    }

    private ClaimEvent event(UUID claim, ClaimEventType type, long version, ClaimStatus status, String officer,
                             String estimated, String assessed, Market market, Instant closedAt) {
        return new ClaimEvent(UUID.randomUUID(), type, claim, "CLM-" + claim.toString().substring(0, 4), version,
                T0.plusSeconds(version), market, ClaimType.MOTOR, status, officer, "SGD", new BigDecimal(estimated),
                assessed == null ? null : new BigDecimal(assessed), T0, closedAt);
    }

    @Test
    void duplicateAndStaleEventsAreIgnored() {
        UUID id = UUID.randomUUID();
        ClaimEvent submitted = event(id, ClaimEventType.CLAIM_SUBMITTED, 0, ClaimStatus.SUBMITTED, null, "1000", null, Market.SG, null);
        ClaimEvent assigned = event(id, ClaimEventType.CLAIM_ASSIGNED, 1, ClaimStatus.UNDER_REVIEW, "o1", "1000", null, Market.SG, null);

        assertThat(projection.apply(submitted)).isTrue();
        assertThat(projection.apply(assigned)).isTrue();
        assertThat(projection.apply(assigned)).isFalse();    // duplicate delivery
        assertThat(projection.apply(submitted)).isFalse();   // stale / out of order
        assertThat(views.findById(id).orElseThrow().getStatus()).isEqualTo(ClaimStatus.UNDER_REVIEW);
    }

    @Test
    void exposureCoversOpenClaimsOnlyAndPrefersAssessment() {
        UUID a = UUID.randomUUID(), b = UUID.randomUUID(), c = UUID.randomUUID(), d = UUID.randomUUID();
        projection.apply(event(a, ClaimEventType.CLAIM_SUBMITTED, 0, ClaimStatus.SUBMITTED, null, "1000", null, Market.SG, null));
        projection.apply(event(b, ClaimEventType.CLAIM_ASSESSED, 2, ClaimStatus.UNDER_REVIEW, "o1", "5000", "4000", Market.SG, null));
        projection.apply(event(c, ClaimEventType.CLAIM_SETTLED, 5, ClaimStatus.SETTLED, "o1", "9000", "8000", Market.SG, T0.plusSeconds(7200)));
        projection.apply(event(d, ClaimEventType.CLAIM_SUBMITTED, 0, ClaimStatus.SUBMITTED, null, "700", null, Market.HK, null));

        List<Exposure> sg = reports.exposure(Market.SG);
        assertThat(sg).hasSize(1);
        assertThat(sg.get(0).openClaims()).isEqualTo(2);
        assertThat(sg.get(0).totalExposure()).isEqualByComparingTo("5000");   // 1000 estimate + 4000 assessment
        assertThat(reports.exposure(null)).hasSize(2);
    }

    @Test
    void workloadPerformanceAndSummary() throws Exception {
        UUID a = UUID.randomUUID(), b = UUID.randomUUID(), c = UUID.randomUUID();
        projection.apply(event(a, ClaimEventType.CLAIM_ASSIGNED, 1, ClaimStatus.UNDER_REVIEW, "o1", "100", null, Market.SG, null));
        projection.apply(event(b, ClaimEventType.CLAIM_ASSIGNED, 1, ClaimStatus.INFO_REQUESTED, "o1", "100", null, Market.SG, null));
        projection.apply(event(c, ClaimEventType.CLAIM_SETTLED, 5, ClaimStatus.SETTLED, "o2", "100", "90", Market.SG, T0.plusSeconds(7200)));

        assertThat(reports.workload()).singleElement().satisfies(w -> {
            assertThat(w.officerId()).isEqualTo("o1");
            assertThat(w.openClaims()).isEqualTo(2);
        });
        assertThat(reports.performance()).singleElement().satisfies(p -> {
            assertThat(p.officerId()).isEqualTo("o2");
            assertThat(p.settled()).isEqualTo(1);
            assertThat(p.averageResolutionHours()).isEqualTo(2.0);
        });

        mvc.perform(get("/api/reports/summary")).andExpect(status().isOk())
                .andExpect(jsonPath("$.openClaims").value(2))
                .andExpect(jsonPath("$.claimsByStatus.SETTLED").value(1));
        mvc.perform(get("/api/reports/exposure?market=SG")).andExpect(jsonPath("$[0].openClaims").value(2));
    }

    @Test
    void eventJsonRoundTripsThroughJackson() throws Exception {
        ClaimEvent e = event(UUID.randomUUID(), ClaimEventType.CLAIM_SUBMITTED, 0, ClaimStatus.SUBMITTED, null, "12.50", null, Market.TH, null);
        assertThat(json.readValue(json.writeValueAsString(e), ClaimEvent.class)).isEqualTo(e);
    }
}
