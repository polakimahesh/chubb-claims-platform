package com.chubb.claims.exception;

public class DocumentTooLargeException extends RuntimeException {
    public DocumentTooLargeException(long maxBytes) {
        super("Document exceeds the maximum size of " + maxBytes / (1024 * 1024) + " MB");
    }
}
