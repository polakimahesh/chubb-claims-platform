package com.chubb.claims.exception;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ProblemDetail;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void unexpectedErrorsBecomeGeneric500WithoutLeakingInternals() {
        ProblemDetail pd = handler.unexpected(new IllegalStateException("jdbc:postgresql://secret-host password=hunter2"));
        assertThat(pd.getStatus()).isEqualTo(500);
        assertThat(pd.getDetail()).doesNotContain("secret-host", "hunter2");
    }

    @Test
    void dataIntegrityViolationIs409WithoutLeakingSql() {
        ProblemDetail pd = handler.dataIntegrity(new DataIntegrityViolationException("duplicate key value violates unique constraint"));
        assertThat(pd.getStatus()).isEqualTo(409);
        assertThat(pd.getDetail()).doesNotContain("constraint");
    }
}
