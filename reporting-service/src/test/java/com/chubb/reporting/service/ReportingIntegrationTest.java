package com.chubb.reporting.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chubb.claims.events.ClaimEvent;
import com.chubb.claims.events.ClaimEventType;
import com.chubb.claims.events.ClaimStatus;
import com.chubb.claims.events.ClaimType;
import com.chubb.claims.events.Market;
import com.chubb.reporting.dto.ReportResponses.Exposure;
import com.chubb.reporting.dto.ReportResponses.SlaBreach;
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
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/** Exercises projection and reports against the real schema; the Kafka listener is not started. */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "spring.kafka.listener.auto-startup=false")
class ReportingIntegrationTest {

    private static final Instant T0 = Instant.parse("2026-01-01T00:00:00Z");
    private static final RequestPostProcessor MANAGER = httpBasic("manager-1", "test-only-password");
    private static final RequestPostProcessor OFFICER = httpBasic("officer-1", "test-only-password");
    private static final RequestPostProcessor CLAIMANT = httpBasic("tan@example.com", "test-only-password");

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
                             String estimated, String assessed, Market market, Instant submittedAt, Instant closedAt) {
        return new ClaimEvent(UUID.randomUUID(), type, claim, "CLM-" + claim.toString().substring(0, 4), version,
                submittedAt.plusSeconds(version), market, ClaimType.MOTOR, status, officer, market.currency(),
                new BigDecimal(estimated), assessed == null ? null : new BigDecimal(assessed), submittedAt, closedAt,
                "c@example.com", "Claimant", null);
    }

    private ClaimEvent event(UUID claim, ClaimEventType type, long version, ClaimStatus status, String officer,
                             String estimated, String assessed, Market market, Instant closedAt) {
        return event(claim, type, version, status, officer, estimated, assessed, market, T0, closedAt);
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
    void exposureTotalConvertsAcrossCurrencies() {
        // SGD 1000 (open) and HKD 1000 (open); rates to USD: SGD 0.74, HKD 0.128, AUD 0.65
        projection.apply(event(UUID.randomUUID(), ClaimEventType.CLAIM_SUBMITTED, 0, ClaimStatus.SUBMITTED, null, "1000", null, Market.SG, null));
        projection.apply(event(UUID.randomUUID(), ClaimEventType.CLAIM_SUBMITTED, 0, ClaimStatus.SUBMITTED, null, "1000", null, Market.HK, null));

        var inUsd = reports.exposureTotal("USD");
        assertThat(inUsd.totalExposure()).isEqualByComparingTo("868.00");            // 740 + 128
        assertThat(inUsd.breakdown()).hasSize(2);
        assertThat(inUsd.rateSource()).contains("not live");
        var inSgd = reports.exposureTotal("sgd");                                    // case-insensitive
        assertThat(inSgd.totalExposure()).isEqualByComparingTo("1172.97");           // 1000 + 1000*0.128/0.74
        assertThat(inSgd.baseCurrency()).isEqualTo("SGD");
    }

    @Test
    void workloadPerformanceAndSummary() throws Exception {
        UUID a = UUID.randomUUID(), b = UUID.randomUUID(), c = UUID.randomUUID(), d = UUID.randomUUID();
        projection.apply(event(a, ClaimEventType.CLAIM_ASSIGNED, 1, ClaimStatus.UNDER_REVIEW, "o1", "100", null, Market.SG, null));
        projection.apply(event(b, ClaimEventType.CLAIM_ASSIGNED, 1, ClaimStatus.INFO_REQUESTED, "o1", "100", null, Market.SG, null));
        projection.apply(event(c, ClaimEventType.CLAIM_SETTLED, 5, ClaimStatus.SETTLED, "o2", "100", "90", Market.SG, T0.plusSeconds(7200)));
        projection.apply(event(d, ClaimEventType.CLAIM_REJECTED, 3, ClaimStatus.REJECTED, "o2", "100", null, Market.SG, T0.plusSeconds(21600)));

        assertThat(reports.workload()).singleElement().satisfies(w -> {
            assertThat(w.officerId()).isEqualTo("o1");
            assertThat(w.openClaims()).isEqualTo(2);
        });
        assertThat(reports.performance()).singleElement().satisfies(p -> {
            assertThat(p.officerId()).isEqualTo("o2");
            assertThat(p.closedClaims()).isEqualTo(2);
            assertThat(p.settled()).isEqualTo(1);
            assertThat(p.rejected()).isEqualTo(1);
            assertThat(p.averageResolutionHours()).isEqualTo(4.0);   // (2h + 6h) / 2
        });

        mvc.perform(get("/api/reports/summary").with(MANAGER)).andExpect(status().isOk())
                .andExpect(jsonPath("$.openClaims").value(2))
                .andExpect(jsonPath("$.claimsByStatus.SETTLED").value(1));
        mvc.perform(get("/api/reports/exposure?market=SG").with(MANAGER)).andExpect(jsonPath("$[0].openClaims").value(2));
        mvc.perform(get("/api/reports/exposure/total?baseCurrency=USD").with(MANAGER))
                .andExpect(jsonPath("$.baseCurrency").value("USD")).andExpect(jsonPath("$.openClaims").value(2));
    }

    @Test
    void slaBreachesFlagOldUnassignedAndLongOpenClaimsOnly() {
        Instant now = Instant.now();
        UUID oldUnassigned = UUID.randomUUID(), freshUnassigned = UUID.randomUUID(), longOpen = UUID.randomUUID(),
                recentOpen = UUID.randomUUID(), waitingOnClaimant = UUID.randomUUID();
        projection.apply(event(oldUnassigned, ClaimEventType.CLAIM_SUBMITTED, 0, ClaimStatus.SUBMITTED, null, "1", null, Market.SG, now.minusSeconds(30 * 3600), null));
        projection.apply(event(freshUnassigned, ClaimEventType.CLAIM_SUBMITTED, 0, ClaimStatus.SUBMITTED, null, "1", null, Market.SG, now.minusSeconds(3600), null));
        projection.apply(event(longOpen, ClaimEventType.CLAIM_ASSIGNED, 1, ClaimStatus.UNDER_REVIEW, "o1", "1", null, Market.SG, now.minusSeconds(10 * 86400), null));
        projection.apply(event(recentOpen, ClaimEventType.CLAIM_ASSIGNED, 1, ClaimStatus.UNDER_REVIEW, "o1", "1", null, Market.SG, now.minusSeconds(2 * 86400), null));
        projection.apply(event(waitingOnClaimant, ClaimEventType.INFO_REQUESTED, 2, ClaimStatus.INFO_REQUESTED, "o1", "1", null, Market.SG, now.minusSeconds(20 * 86400), null));

        List<SlaBreach> breaches = reports.slaBreaches();
        assertThat(breaches).extracting(SlaBreach::claimId).containsExactly(longOpen, oldUnassigned);   // oldest first
        assertThat(breaches.get(0).breachType().name()).isEqualTo("OPEN_TOO_LONG");
        assertThat(breaches.get(1).breachType().name()).isEqualTo("UNASSIGNED_TOO_LONG");
        assertThat(breaches.get(1).ageHours()).isBetween(29L, 31L);
    }

    @Test
    void reportsRequireTheManagerRole() throws Exception {
        mvc.perform(get("/api/reports/summary")).andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
        mvc.perform(get("/api/reports/summary").with(OFFICER)).andExpect(status().isForbidden());
        mvc.perform(get("/api/reports/exposure").with(CLAIMANT)).andExpect(status().isForbidden());
        mvc.perform(get("/api/reports/sla-breaches").with(OFFICER)).andExpect(status().isForbidden());
        mvc.perform(get("/api/reports/sla-breaches").with(MANAGER)).andExpect(status().isOk());
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }

    @Test
    void unsupportedBaseCurrencyIs400() throws Exception {
        mvc.perform(get("/api/reports/exposure/total?baseCurrency=XYZ").with(MANAGER))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void eventJsonRoundTripsThroughJackson() throws Exception {
        ClaimEvent e = event(UUID.randomUUID(), ClaimEventType.CLAIM_SUBMITTED, 0, ClaimStatus.SUBMITTED, null, "12.50", null, Market.TH, null);
        assertThat(json.readValue(json.writeValueAsString(e), ClaimEvent.class)).isEqualTo(e);
    }
}
