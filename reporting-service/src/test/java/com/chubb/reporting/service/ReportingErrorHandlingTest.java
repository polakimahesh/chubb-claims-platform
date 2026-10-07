package com.chubb.reporting.service;

import static org.assertj.core.api.Assertions.assertThat;
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

/** Reporting API error paths: all failures are RFC 7807 problems. */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "spring.kafka.listener.auto-startup=false")
class ReportingErrorHandlingTest {

    @Autowired MockMvc mvc;

    @Test
    void unknownMarketFilterIs400Problem() throws Exception {
        mvc.perform(get("/api/reports/exposure?market=MARS"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void unknownRouteIs404AndWrongMethodIs405() throws Exception {
        mvc.perform(get("/api/reports/nope")).andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
        mvc.perform(post("/api/reports/summary")).andExpect(status().isMethodNotAllowed())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void emptyReadModelReturnsEmptyReportsNotErrors() throws Exception {
        mvc.perform(get("/api/reports/workload")).andExpect(status().isOk());
        mvc.perform(get("/api/reports/performance")).andExpect(status().isOk());
    }

    @Test
    void unexpectedErrorsAreGeneric500() {
        ProblemDetail pd = new GlobalExceptionHandler().unexpected(new IllegalStateException("db password=hunter2"));
        assertThat(pd.getStatus()).isEqualTo(500);
        assertThat(pd.getDetail()).doesNotContain("hunter2");
    }
}
