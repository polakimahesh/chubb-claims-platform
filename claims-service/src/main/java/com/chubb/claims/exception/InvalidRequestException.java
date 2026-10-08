package com.chubb.claims.exception;

/** A request that is syntactically valid but semantically wrong (e.g. currency not matching the market). */
public class InvalidRequestException extends RuntimeException {
    public InvalidRequestException(String message) {
        super(message);
    }
}
