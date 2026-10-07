package com.chubb.claims.exception;

/** A well-formed request that violates a business rule (e.g. approving without an assessment). */
public class BusinessRuleException extends RuntimeException {
    public BusinessRuleException(String message) {
        super(message);
    }
}
