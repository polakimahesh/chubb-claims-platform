package com.chubb.claims.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** Negative paths: every failure must come back as an RFC 7807 problem with the right status. */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "claims.outbox.enabled=false")
class NegativeScenariosIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    private static final String VALID = """
            {"claimantName":"Neg Test","claimantEmail":"neg@example.com","market":"SG","claimType":"MOTOR",
             "description":"d","incidentDate":"2026-01-10","currency":"SGD","estimatedAmount":100}""";

    private String submit() throws Exception {
        String body = mvc.perform(post("/api/claims").contentType(MediaType.APPLICATION_JSON).content(VALID))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return json.readTree(body).get("id").asText();
    }

    private ResultActions staff(String url, String officer, String body) throws Exception {
        MockHttpServletRequestBuilder r = post(url).contentType(MediaType.APPLICATION_JSON).content(body);
        if (officer != null) {
            r.header("X-Officer-Id", officer);
        }
        return mvc.perform(r);
    }

    private ResultActions expectProblem(ResultActions r, int status) throws Exception {
        return r.andExpect(status().is(status))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(status));
    }

    // ---------- malformed / invalid input -> 400 problem ----------

    @Test
    void malformedJsonIs400() throws Exception {
        expectProblem(mvc.perform(post("/api/claims").contentType(MediaType.APPLICATION_JSON).content("{not json")), 400);
    }

    @Test
    void unknownEnumValueIs400() throws Exception {
        expectProblem(mvc.perform(post("/api/claims").contentType(MediaType.APPLICATION_JSON)
                .content(VALID.replace("\"SG\"", "\"MARS\""))), 400);
    }

    @Test
    void futureIncidentDateAndBadCurrencyAreFieldErrors() throws Exception {
        String body = VALID.replace("2026-01-10", "2999-01-01").replace("SGD", "sg");
        expectProblem(mvc.perform(post("/api/claims").contentType(MediaType.APPLICATION_JSON).content(body)), 400)
                .andExpect(jsonPath("$.errors.length()").value(2));
    }

    @Test
    void emptyBodyIs400() throws Exception {
        expectProblem(mvc.perform(post("/api/claims").contentType(MediaType.APPLICATION_JSON)), 400);
    }

    @Test
    void invalidUuidInPathIs400() throws Exception {
        expectProblem(mvc.perform(get("/api/claims/not-a-uuid")), 400);
    }

    @Test
    void missingRequiredQueryParamIs400() throws Exception {
        expectProblem(mvc.perform(get("/api/claims")), 400);
        expectProblem(mvc.perform(get("/api/staff/claims")), 400);
    }

    @Test
    void invalidFilterEnumIs400() throws Exception {
        expectProblem(mvc.perform(get("/api/staff/claims/queue?market=XX")), 400);
    }

    @Test
    void missingOrBlankOfficerHeaderIs400() throws Exception {
        String id = submit();
        expectProblem(staff("/api/staff/claims/" + id + "/assign", null, ""), 400);
        expectProblem(staff("/api/staff/claims/" + id + "/assign", "  ", ""), 400);
    }

    @Test
    void blankReasonOrQuestionOrAmountIs400() throws Exception {
        String id = submit();
        staff("/api/staff/claims/" + id + "/assign", "o1", "").andExpect(status().isOk());
        expectProblem(staff("/api/staff/claims/" + id + "/reject", "o1", "{\"reason\":\" \"}"), 400);
        expectProblem(staff("/api/staff/claims/" + id + "/info-requests", "o1", "{}"), 400);
        expectProblem(staff("/api/staff/claims/" + id + "/assess", "o1", "{\"assessedAmount\":0}"), 400);
    }

    @Test
    void unknownRouteIs404AndWrongMethodIs405() throws Exception {
        expectProblem(mvc.perform(get("/api/nope")), 404);
        expectProblem(mvc.perform(get("/api/staff/claims/" + UUID.randomUUID() + "/assign")), 405);
    }

    // ---------- not found ----------

    @Test
    void unknownClaimIs404OnEveryEndpoint() throws Exception {
        String missing = UUID.randomUUID().toString();
        expectProblem(mvc.perform(get("/api/claims/" + missing + "/history")), 404);
        expectProblem(mvc.perform(get("/api/claims/" + missing + "/info-requests")), 404);
        expectProblem(staff("/api/staff/claims/" + missing + "/assign", "o1", ""), 404);
        expectProblem(staff("/api/staff/claims/" + missing + "/settle", "o1", ""), 404);
        expectProblem(staff("/api/claims/" + missing + "/info-requests/" + UUID.randomUUID() + "/response", null,
                "{\"response\":\"x\"}"), 404);
    }

    // ---------- business rules ----------

    @Test
    void informationRequestEdgeCases() throws Exception {
        String id = submit();
        String other = submit();
        staff("/api/staff/claims/" + id + "/assign", "o1", "").andExpect(status().isOk());
        String info = staff("/api/staff/claims/" + id + "/info-requests", "o1", "{\"question\":\"Photos?\"}")
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String requestId = json.readTree(info).get("id").asText();

        // an officer who does not own the claim cannot ask for info
        expectProblem(staff("/api/staff/claims/" + id + "/info-requests", "o2", "{\"question\":\"x\"}"), 422);
        // cannot answer on a different claim, or with an unknown request id
        expectProblem(staff("/api/claims/" + other + "/info-requests/" + requestId + "/response", null,
                "{\"response\":\"x\"}"), 422);
        expectProblem(staff("/api/claims/" + id + "/info-requests/" + UUID.randomUUID() + "/response", null,
                "{\"response\":\"x\"}"), 422);
        // cannot assess or approve while waiting on the claimant
        expectProblem(staff("/api/staff/claims/" + id + "/assess", "o1", "{\"assessedAmount\":10}"), 409);
        // answer once ok, twice rejected
        staff("/api/claims/" + id + "/info-requests/" + requestId + "/response", null, "{\"response\":\"Here\"}")
                .andExpect(status().isOk());
        expectProblem(staff("/api/claims/" + id + "/info-requests/" + requestId + "/response", null,
                "{\"response\":\"again\"}"), 422);
    }

    @Test
    void actionsOnUnassignedOrClosedClaimsAreConflicts() throws Exception {
        String id = submit();
        expectProblem(staff("/api/staff/claims/" + id + "/approve", "o1", ""), 409);   // not picked up yet
        expectProblem(staff("/api/staff/claims/" + id + "/reject", "o1", "{\"reason\":\"r\"}"), 409); // not picked up
        expectProblem(staff("/api/staff/claims/" + id + "/info-requests", "o1", "{\"question\":\"q\"}"), 409);
        staff("/api/staff/claims/" + id + "/assign", "o1", "").andExpect(status().isOk());
        staff("/api/staff/claims/" + id + "/reject", "o1", "{\"reason\":\"r\"}").andExpect(status().isOk());
        for (String action : List.of("assign", "approve", "settle")) {
            expectProblem(staff("/api/staff/claims/" + id + "/" + action, "o1", ""), 409);
        }
        expectProblem(staff("/api/staff/claims/" + id + "/assess", "o1", "{\"assessedAmount\":5}"), 409);
    }

    @Test
    void outOfRangePagingIsClampedNotAnError() throws Exception {
        mvc.perform(get("/api/staff/claims/queue?page=-5&size=100000"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.size").value(100)).andExpect(jsonPath("$.page").value(0));
    }

    // ---------- concurrency ----------

    @Test
    void twoOfficersPickingUpTheSameClaimAtOnceYieldExactlyOneWinner() throws Exception {
        String id = submit();
        CountDownLatch go = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            List<Callable<Integer>> tasks = List.of(pickUp(id, "o1", go), pickUp(id, "o2", go));
            List<Future<Integer>> futures = tasks.stream().map(pool::submit).toList();
            go.countDown();
            List<Integer> codes = List.of(futures.get(0).get(), futures.get(1).get());
            assertThat(codes).containsExactlyInAnyOrder(200, 409);
        } finally {
            pool.shutdownNow();
        }
        String owner = json.readTree(mvc.perform(get("/api/claims/" + id)).andReturn().getResponse().getContentAsString())
                .get("assignedOfficerId").asText();
        assertThat(owner).isIn("o1", "o2");
    }

    private Callable<Integer> pickUp(String id, String officer, CountDownLatch go) {
        return () -> {
            go.await();
            return staff("/api/staff/claims/" + id + "/assign", officer, "").andReturn().getResponse().getStatus();
        };
    }
}
