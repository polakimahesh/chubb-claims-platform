package com.chubb.claims.support;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;

import org.springframework.test.web.servlet.request.RequestPostProcessor;

/** HTTP Basic credentials of the demo users created by the shared security module (local-development password). */
public final class TestAuth {
    public static final String PASSWORD = "test-only-password";   // matches src/test/resources/application.properties

    private TestAuth() {
    }

    public static RequestPostProcessor claimant() {
        return httpBasic("tan@example.com", PASSWORD);
    }

    public static RequestPostProcessor otherClaimant() {
        return httpBasic("lee@example.com", PASSWORD);
    }

    public static RequestPostProcessor officer1() {
        return httpBasic("officer-1", PASSWORD);
    }

    public static RequestPostProcessor officer2() {
        return httpBasic("officer-2", PASSWORD);
    }

    public static RequestPostProcessor manager() {
        return httpBasic("manager-1", PASSWORD);
    }

    public static RequestPostProcessor wrongPassword() {
        return httpBasic("officer-1", "nope");
    }
}
