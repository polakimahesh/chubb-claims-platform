package com.chubb.claims.controller;

import static com.chubb.claims.support.TestAuth.claimant;
import static com.chubb.claims.support.TestAuth.officer1;
import static com.chubb.claims.support.TestAuth.officer2;
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
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/** Negative paths: every failure must come back as an RFC 7807 problem with the right status. */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "claims.outbox.enabled=false")
class NegativeScenariosIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    private static final String VALID = """
            {"claimantName":"Neg Test","market":"SG","claimType":"MOTOR",
             "description":"d","incidentDate":"2026-01-10","currency":"SGD","estimatedAmount":100}""";

    private String submit() throws Exception {
        String body = mvc.perform(post("/api/claims").with(claimant()).contentType(MediaType.APPLICATION_JSON)
                        .content(VALID))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return json.readTree(body).get("id").asText();
    }

    private ResultActions as(RequestPostProcessor user, String url, String body) throws Exception {
        return mvc.perform(post(url).with(user).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private ResultActions expectProblem(ResultActions r, int status) throws Exception {
        return r.andExpect(status().is(status))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(status));
    }

    // ---------- malformed / invalid input -> 400 problem ----------

    @Test
    void malformedJsonIs400() throws Exception {
        expectProblem(as(claimant(), "/api/claims", "{not json"), 400);
    }

    @Test
    void unknownEnumValueIs400() throws Exception {
        expectProblem(as(claimant(), "/api/claims", VALID.replace("\"SG\"", "\"MARS\"")), 400);
    }

    @Test
    void futureIncidentDateAndBadCurrencyAreFieldErrors() throws Exception {
        String body = VALID.replace("2026-01-10", "2999-01-01").replace("SGD", "sg");
        expectProblem(as(claimant(), "/api/claims", body), 400).andExpect(jsonPath("$.errors.length()").value(2));
    }

    @Test
    void currencyMustMatchTheMarket() throws Exception {
        expectProblem(as(claimant(), "/api/claims", VALID.replace("SGD", "USD")), 400);
        expectProblem(as(claimant(), "/api/claims", VALID.replace("\"SG\"", "\"AU\"")), 400);
    }

    @Test
    void emptyBodyIs400() throws Exception {
        expectProblem(mvc.perform(post("/api/claims").with(claimant()).contentType(MediaType.APPLICATION_JSON)), 400);
    }

    @Test
    void invalidUuidInPathIs400() throws Exception {
        expectProblem(mvc.perform(get("/api/claims/not-a-uuid").with(claimant())), 400);
    }

    @Test
    void invalidFilterEnumIs400() throws Exception {
        expectProblem(mvc.perform(get("/api/staff/claims/queue?market=XX").with(officer1())), 400);
    }

    @Test
    void blankReasonOrQuestionOrAmountIs400() throws Exception {
        String id = submit();
        as(officer1(), "/api/staff/claims/" + id + "/assign", "").andExpect(status().isOk());
        expectProblem(as(officer1(), "/api/staff/claims/" + id + "/reject", "{\"reason\":\" \"}"), 400);
        expectProblem(as(officer1(), "/api/staff/claims/" + id + "/info-requests", "{}"), 400);
        expectProblem(as(officer1(), "/api/staff/claims/" + id + "/assess", "{\"assessedAmount\":0}"), 400);
        expectProblem(as(officer1(), "/api/staff/claims/" + id + "/reassign", "{}"), 400);
    }

    @Test
    void unknownRouteIsDeniedAndWrongMethodIs405() throws Exception {
        expectProblem(mvc.perform(get("/api/nope").with(claimant())), 403);   // deny-by-default
        expectProblem(mvc.perform(get("/api/staff/claims/" + UUID.randomUUID() + "/assign").with(officer1())), 405);
    }

    // ---------- not found ----------

    @Test
    void unknownClaimIs404OnEveryEndpoint() throws Exception {
        String missing = UUID.randomUUID().toString();
        expectProblem(mvc.perform(get("/api/claims/" + missing + "/history").with(claimant())), 404);
        expectProblem(mvc.perform(get("/api/claims/" + missing + "/info-requests").with(claimant())), 404);
        expectProblem(as(officer1(), "/api/staff/claims/" + missing + "/assign", ""), 404);
        expectProblem(as(officer1(), "/api/staff/claims/" + missing + "/settle", ""), 404);
        expectProblem(as(claimant(), "/api/claims/" + missing + "/info-requests/" + UUID.randomUUID() + "/response",
                "{\"response\":\"x\"}"), 404);
    }

    // ---------- business rules ----------

    @Test
    void informationRequestEdgeCases() throws Exception {
        String id = submit();
        String other = submit();
        as(officer1(), "/api/staff/claims/" + id + "/assign", "").andExpect(status().isOk());
        String info = as(officer1(), "/api/staff/claims/" + id + "/info-requests", "{\"question\":\"Photos?\"}")
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String requestId = json.readTree(info).get("id").asText();

        expectProblem(as(officer2(), "/api/staff/claims/" + id + "/info-requests", "{\"question\":\"x\"}"), 422);
        expectProblem(as(claimant(), "/api/claims/" + other + "/info-requests/" + requestId + "/response",
                "{\"response\":\"x\"}"), 422);
        expectProblem(as(claimant(), "/api/claims/" + id + "/info-requests/" + UUID.randomUUID() + "/response",
                "{\"response\":\"x\"}"), 422);
        expectProblem(as(officer1(), "/api/staff/claims/" + id + "/assess", "{\"assessedAmount\":10}"), 409);
        as(claimant(), "/api/claims/" + id + "/info-requests/" + requestId + "/response", "{\"response\":\"Here\"}")
                .andExpect(status().isOk());
        expectProblem(as(claimant(), "/api/claims/" + id + "/info-requests/" + requestId + "/response",
                "{\"response\":\"again\"}"), 422);
    }

    @Test
    void actionsOnUnassignedOrClosedClaimsAreConflicts() throws Exception {
        String id = submit();
        expectProblem(as(officer1(), "/api/staff/claims/" + id + "/approve", ""), 409);
        expectProblem(as(officer1(), "/api/staff/claims/" + id + "/reject", "{\"reason\":\"r\"}"), 409);
        expectProblem(as(officer1(), "/api/staff/claims/" + id + "/info-requests", "{\"question\":\"q\"}"), 409);
        expectProblem(as(officer1(), "/api/staff/claims/" + id + "/reassign", "{\"toOfficerId\":\"officer-2\"}"), 409);
        as(officer1(), "/api/staff/claims/" + id + "/assign", "").andExpect(status().isOk());
        expectProblem(as(officer1(), "/api/staff/claims/" + id + "/approve", ""), 422);   // assigned but not assessed
        as(officer1(), "/api/staff/claims/" + id + "/reject", "{\"reason\":\"r\"}").andExpect(status().isOk());
        for (String action : List.of("assign", "approve", "settle")) {
            expectProblem(as(officer1(), "/api/staff/claims/" + id + "/" + action, ""), 409);
        }
        expectProblem(as(officer1(), "/api/staff/claims/" + id + "/assess", "{\"assessedAmount\":5}"), 409);
    }

    @Test
    void outOfRangePagingIsClampedNotAnError() throws Exception {
        mvc.perform(get("/api/staff/claims/queue?page=-5&size=100000").with(officer1()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.size").value(100)).andExpect(jsonPath("$.page").value(0));
    }

    // ---------- concurrency ----------

    @Test
    void twoOfficersPickingUpTheSameClaimAtOnceYieldExactlyOneWinner() throws Exception {
        String id = submit();
        CountDownLatch go = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            List<Callable<Integer>> tasks = List.of(pickUp(id, officer1(), go), pickUp(id, officer2(), go));
            List<Future<Integer>> futures = tasks.stream().map(pool::submit).toList();
            go.countDown();
            List<Integer> codes = List.of(futures.get(0).get(), futures.get(1).get());
            assertThat(codes).containsExactlyInAnyOrder(200, 409);
        } finally {
            pool.shutdownNow();
        }
    }

    private Callable<Integer> pickUp(String id, RequestPostProcessor officer, CountDownLatch go) {
        return () -> {
            go.await();
            return as(officer, "/api/staff/claims/" + id + "/assign", "").andReturn().getResponse().getStatus();
        };
    }
}
