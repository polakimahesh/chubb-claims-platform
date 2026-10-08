package com.chubb.notification.service;

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
import com.chubb.notification.repository.NotificationRepository;
import java.math.BigDecimal;
import java.time.Instant;
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

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "spring.kafka.listener.auto-startup=false")
class NotificationServiceIntegrationTest {

    private static final RequestPostProcessor TAN = httpBasic("tan@example.com", "test-only-password");
    private static final RequestPostProcessor LEE = httpBasic("lee@example.com", "test-only-password");
    private static final RequestPostProcessor MANAGER = httpBasic("manager-1", "test-only-password");

    @Autowired NotificationService service;
    @Autowired NotificationRepository repository;
    @Autowired MockMvc mvc;

    @BeforeEach
    void clean() {
        repository.deleteAll();
    }

    private ClaimEvent event(ClaimEventType type, String email, String note) {
        return new ClaimEvent(UUID.randomUUID(), type, UUID.randomUUID(), "CLM-SG-2026-ABCD1234", 1, Instant.now(),
                Market.SG, ClaimType.MOTOR, ClaimStatus.UNDER_REVIEW, "officer-1", "SGD", new BigDecimal("100"), null,
                Instant.now(), null, email, "Tan Wei", note);
    }

    @Test
    void claimantIsNotifiedOfClaimantFacingMilestones() {
        for (ClaimEventType type : new ClaimEventType[] {ClaimEventType.CLAIM_SUBMITTED, ClaimEventType.CLAIM_ASSIGNED,
                ClaimEventType.INFO_REQUESTED, ClaimEventType.CLAIM_APPROVED, ClaimEventType.CLAIM_REJECTED,
                ClaimEventType.CLAIM_SETTLED}) {
            assertThat(service.handle(event(type, "tan@example.com", "reason or question"))).as(type.name()).isTrue();
        }
        assertThat(repository.count()).isEqualTo(6);
    }

    @Test
    void internalEventsDoNotNotify() {
        for (ClaimEventType type : new ClaimEventType[] {ClaimEventType.CLAIM_ASSESSED, ClaimEventType.CLAIM_REASSIGNED,
                ClaimEventType.INFO_PROVIDED}) {
            assertThat(service.handle(event(type, "tan@example.com", null))).as(type.name()).isFalse();
        }
        assertThat(repository.count()).isZero();
    }

    @Test
    void redeliveredEventNotifiesOnlyOnce() {
        ClaimEvent e = event(ClaimEventType.CLAIM_APPROVED, "tan@example.com", null);
        assertThat(service.handle(e)).isTrue();
        assertThat(service.handle(e)).isFalse();
        assertThat(repository.count()).isEqualTo(1);
    }

    @Test
    void eventWithoutRecipientIsIgnoredAndMessagesCarryTheReason() {
        assertThat(service.handle(event(ClaimEventType.CLAIM_APPROVED, null, null))).isFalse();
        service.handle(event(ClaimEventType.CLAIM_REJECTED, "tan@example.com", "Policy lapsed"));
        service.handle(event(ClaimEventType.INFO_REQUESTED, "tan@example.com", "Please send the police report"));
        assertThat(repository.findAll()).anySatisfy(n -> assertThat(n.getBody()).contains("Policy lapsed"))
                .anySatisfy(n -> assertThat(n.getBody()).contains("Please send the police report"));
    }

    @Test
    void claimantsSeeOnlyTheirOwnManagersSeeAll() throws Exception {
        service.handle(event(ClaimEventType.CLAIM_SUBMITTED, "tan@example.com", null));
        service.handle(event(ClaimEventType.CLAIM_SUBMITTED, "lee@example.com", null));

        mvc.perform(get("/api/notifications").with(TAN)).andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(1))
                .andExpect(jsonPath("$.items[0].recipientEmail").value("tan@example.com"));
        // a claimant cannot read someone else's by passing their e-mail
        mvc.perform(get("/api/notifications?claimantEmail=lee@example.com").with(TAN))
                .andExpect(jsonPath("$.items[0].recipientEmail").value("tan@example.com"));
        mvc.perform(get("/api/notifications").with(LEE)).andExpect(jsonPath("$.items[0].recipientEmail").value("lee@example.com"));
        mvc.perform(get("/api/notifications").with(MANAGER)).andExpect(jsonPath("$.totalItems").value(2));
        mvc.perform(get("/api/notifications?claimantEmail=lee@example.com").with(MANAGER))
                .andExpect(jsonPath("$.totalItems").value(1));
    }

    @Test
    void authenticationIsRequired() throws Exception {
        mvc.perform(get("/api/notifications")).andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
        mvc.perform(get("/api/notifications").with(httpBasic("tan@example.com", "wrong"))).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/unknown").with(MANAGER)).andExpect(status().isForbidden());
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }

    @Test
    void emailAddressesAreMaskedInLogs() {
        assertThat(LoggingNotificationSender.mask("tan@example.com")).isEqualTo("t***@example.com");
        assertThat(LoggingNotificationSender.mask("a@b.com")).isEqualTo("***");
    }
}
