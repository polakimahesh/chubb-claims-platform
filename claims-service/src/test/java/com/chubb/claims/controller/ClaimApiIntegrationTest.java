package com.chubb.claims.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chubb.claims.entity.OutboxEvent;
import com.chubb.claims.repository.OutboxEventRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/** End-to-end API flow against the real schema (Flyway on H2). The outbox relay is off; we assert on outbox rows. */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "claims.outbox.enabled=false")
class ClaimApiIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired OutboxEventRepository outbox;

    private static final String SUBMIT = """
            {"claimantName":"Tan Wei","claimantEmail":"%s","market":"SG","claimType":"MOTOR",
             "description":"Rear-ended at traffic light","incidentDate":"2026-01-10",
             "currency":"SGD","estimatedAmount":5000.00}""";

    private String submit(String email) throws Exception {
        String body = mvc.perform(post("/api/claims").contentType(MediaType.APPLICATION_JSON)
                        .content(SUBMIT.formatted(email)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("SUBMITTED"))
                .andReturn().getResponse().getContentAsString();
        return json.readTree(body).get("id").asText();
    }

    private void act(String url, String officer, String body, int expected) throws Exception {
        var req = post(url).contentType(MediaType.APPLICATION_JSON).content(body);
        if (officer != null) {
            req.header("X-Officer-Id", officer);
        }
        mvc.perform(req).andExpect(status().is(expected));
    }

    @Test
    void fullLifecycleWithInfoRequestAndOutboxEvents() throws Exception {
        String email = "lifecycle@example.com";
        String id = submit(email);

        // queue shows the claim, then officer picks it up
        mvc.perform(get("/api/staff/claims/queue?market=SG&claimType=MOTOR"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[?(@.id=='" + id + "')]").exists());
        act("/api/staff/claims/" + id + "/assign", "officer-1", "", 200);
        mvc.perform(get("/api/staff/claims?officerId=officer-1&status=UNDER_REVIEW"))
                .andExpect(jsonPath("$.items[?(@.id=='" + id + "')]").exists());

        // officer asks for info; claimant answers; claim returns to review
        String info = mvc.perform(post("/api/staff/claims/" + id + "/info-requests")
                        .header("X-Officer-Id", "officer-1").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"Please upload the police report\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.open").value(true))
                .andReturn().getResponse().getContentAsString();
        String requestId = json.readTree(info).get("id").asText();
        mvc.perform(get("/api/claims/" + id)).andExpect(jsonPath("$.status").value("INFO_REQUESTED"));
        act("/api/claims/" + id + "/info-requests/" + requestId + "/response", null,
                "{\"response\":\"Report no. 12345 attached\"}", 200);
        mvc.perform(get("/api/claims/" + id)).andExpect(jsonPath("$.status").value("UNDER_REVIEW"));

        // assess -> approve -> settle
        act("/api/staff/claims/" + id + "/assess", "officer-1", "{\"assessedAmount\":4200.00}", 200);
        act("/api/staff/claims/" + id + "/approve", "officer-1", "", 200);
        act("/api/staff/claims/" + id + "/settle", "officer-1", "", 200);
        mvc.perform(get("/api/claims/" + id)).andExpect(jsonPath("$.status").value("SETTLED"));
        mvc.perform(get("/api/claims/" + id + "/history")).andExpect(jsonPath("$.length()").value(7));
        mvc.perform(get("/api/claims?claimantEmail=" + email)).andExpect(jsonPath("$.totalItems").value(1));

        // every change produced one ordered outbox event for this claim, with increasing versions
        List<OutboxEvent> events = outbox.findAll().stream().filter(e -> e.getAggregateId().toString().equals(id)).toList();
        assertThat(events).hasSize(7);
        long previous = -1;
        for (OutboxEvent e : events) {
            JsonNode node = json.readTree(e.getPayload());
            assertThat(node.get("version").asLong()).isGreaterThan(previous);
            previous = node.get("version").asLong();
        }
        assertThat(json.readTree(events.get(6).getPayload()).get("type").asText()).isEqualTo("CLAIM_SETTLED");
    }

    @Test
    void rejectedClaimCannotBeSettled() throws Exception {
        String id = submit("reject@example.com");
        act("/api/staff/claims/" + id + "/assign", "officer-2", "", 200);
        act("/api/staff/claims/" + id + "/reject", "officer-2", "{\"reason\":\"Policy lapsed\"}", 200);
        act("/api/staff/claims/" + id + "/settle", "officer-2", "", 409);
    }

    @Test
    void secondOfficerCannotPickUpOrActOnAssignedClaim() throws Exception {
        String id = submit("clash@example.com");
        act("/api/staff/claims/" + id + "/assign", "officer-1", "", 200);
        act("/api/staff/claims/" + id + "/assign", "officer-2", "", 409);
        act("/api/staff/claims/" + id + "/assess", "officer-2", "{\"assessedAmount\":10}", 422);
    }

    @Test
    void approveWithoutAssessmentIsUnprocessable() throws Exception {
        String id = submit("noassess@example.com");
        act("/api/staff/claims/" + id + "/assign", "officer-3", "", 200);
        act("/api/staff/claims/" + id + "/approve", "officer-3", "", 422);
    }

    @Test
    void invalidSubmissionReturnsFieldErrors() throws Exception {
        mvc.perform(post("/api/claims").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"claimantEmail\":\"not-an-email\",\"estimatedAmount\":-1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors").isArray());
    }

    @Test
    void unknownClaimIs404() throws Exception {
        mvc.perform(get("/api/claims/" + java.util.UUID.randomUUID())).andExpect(status().isNotFound());
    }
}
