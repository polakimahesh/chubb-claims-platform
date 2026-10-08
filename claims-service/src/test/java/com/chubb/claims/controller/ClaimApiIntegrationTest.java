package com.chubb.claims.controller;

import static com.chubb.claims.support.TestAuth.claimant;
import static com.chubb.claims.support.TestAuth.officer1;
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

    static final String SUBMIT = """
            {"claimantName":"Tan Wei","market":"SG","claimType":"MOTOR",
             "description":"Rear-ended at traffic light","incidentDate":"2026-01-10",
             "currency":"SGD","estimatedAmount":5000.00}""";

    private String submit() throws Exception {
        String body = mvc.perform(post("/api/claims").with(claimant()).contentType(MediaType.APPLICATION_JSON)
                        .content(SUBMIT))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("SUBMITTED"))
                .andExpect(jsonPath("$.claimantEmail").value("tan@example.com"))
                .andReturn().getResponse().getContentAsString();
        return json.readTree(body).get("id").asText();
    }

    private void staff(String url, String body) throws Exception {
        mvc.perform(post(url).with(officer1()).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk());
    }

    @Test
    void fullLifecycleWithInfoRequestAndOutboxEvents() throws Exception {
        String id = submit();

        mvc.perform(get("/api/staff/claims/queue?market=SG&claimType=MOTOR").with(officer1()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[?(@.id=='" + id + "')]").exists());
        staff("/api/staff/claims/" + id + "/assign", "");
        mvc.perform(get("/api/staff/claims?status=UNDER_REVIEW").with(officer1()))
                .andExpect(jsonPath("$.items[?(@.id=='" + id + "')]").exists());

        String info = mvc.perform(post("/api/staff/claims/" + id + "/info-requests").with(officer1())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"question\":\"Please upload the police report\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.open").value(true))
                .andReturn().getResponse().getContentAsString();
        String requestId = json.readTree(info).get("id").asText();
        mvc.perform(get("/api/claims/" + id).with(claimant())).andExpect(jsonPath("$.status").value("INFO_REQUESTED"));
        mvc.perform(post("/api/claims/" + id + "/info-requests/" + requestId + "/response").with(claimant())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"response\":\"Report no. 12345 attached\"}"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/claims/" + id).with(claimant())).andExpect(jsonPath("$.status").value("UNDER_REVIEW"));

        staff("/api/staff/claims/" + id + "/assess", "{\"assessedAmount\":4200.00}");
        staff("/api/staff/claims/" + id + "/approve", "");
        staff("/api/staff/claims/" + id + "/settle", "");
        mvc.perform(get("/api/claims/" + id).with(claimant())).andExpect(jsonPath("$.status").value("SETTLED"));
        mvc.perform(get("/api/claims/" + id + "/history").with(claimant())).andExpect(jsonPath("$.length()").value(7));
        mvc.perform(get("/api/claims").with(claimant())).andExpect(jsonPath("$.totalItems").isNumber());

        List<OutboxEvent> events = outbox.findAll().stream().filter(e -> e.getAggregateId().toString().equals(id)).toList();
        assertThat(events).hasSize(7);
        long previous = -1;
        for (OutboxEvent e : events) {
            JsonNode node = json.readTree(e.getPayload());
            assertThat(node.get("version").asLong()).isGreaterThan(previous);
            assertThat(node.get("claimantEmail").asText()).isEqualTo("tan@example.com");
            previous = node.get("version").asLong();
        }
        assertThat(json.readTree(events.get(6).getPayload()).get("type").asText()).isEqualTo("CLAIM_SETTLED");
        assertThat(json.readTree(events.get(2).getPayload()).get("note").asText()).isEqualTo("Please upload the police report");
    }

    @Test
    void rejectedClaimCannotBeSettled() throws Exception {
        String id = submit();
        staff("/api/staff/claims/" + id + "/assign", "");
        staff("/api/staff/claims/" + id + "/reject", "{\"reason\":\"Policy lapsed\"}");
        mvc.perform(post("/api/staff/claims/" + id + "/settle").with(officer1())).andExpect(status().isConflict());
    }
}
