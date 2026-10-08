package com.chubb.reporting.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chubb.reporting.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/** Reporting API error paths: all failures are RFC 7807 problems. */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "spring.kafka.listener.auto-startup=false")
class ReportingErrorHandlingTest {

    private static final RequestPostProcessor MANAGER = httpBasic("manager-1", "test-only-password");

    @Autowired MockMvc mvc;

    @Test
    void unknownMarketFilterIs400Problem() throws Exception {
        mvc.perform(get("/api/reports/exposure?market=MARS").with(MANAGER))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void wrongPasswordIs401() throws Exception {
        mvc.perform(get("/api/reports/summary").with(httpBasic("manager-1", "wrong")))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void unknownRouteIsDeniedAndWrongMethodIs405() throws Exception {
        mvc.perform(get("/api/reports/nope").with(MANAGER)).andExpect(status().isNotFound());       // inside the manager area
        mvc.perform(get("/api/other").with(MANAGER)).andExpect(status().isForbidden());             // deny-by-default
        mvc.perform(post("/api/reports/summary").with(MANAGER)).andExpect(status().isMethodNotAllowed())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void emptyReadModelReturnsEmptyReportsNotErrors() throws Exception {
        mvc.perform(get("/api/reports/workload").with(MANAGER)).andExpect(status().isOk());
        mvc.perform(get("/api/reports/performance").with(MANAGER)).andExpect(status().isOk());
    }

    @Test
    void unexpectedErrorsAreGeneric500() {
        ProblemDetail pd = new GlobalExceptionHandler().unexpected(new IllegalStateException("db password=hunter2"));
        assertThat(pd.getStatus()).isEqualTo(500);
        assertThat(pd.getDetail()).doesNotContain("hunter2");
    }
}
