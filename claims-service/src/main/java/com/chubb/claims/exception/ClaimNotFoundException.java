package com.chubb.claims.exception;

import java.util.UUID;

public class ClaimNotFoundException extends RuntimeException {
    public ClaimNotFoundException(UUID id) {
        super("Claim not found: " + id);
    }
}
