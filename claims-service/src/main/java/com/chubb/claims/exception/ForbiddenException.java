package com.chubb.claims.exception;

/** The caller is authenticated but not allowed to do this (e.g. an officer viewing another officer's workload). */
public class ForbiddenException extends RuntimeException {
    public ForbiddenException(String message) {
        super(message);
    }
}
