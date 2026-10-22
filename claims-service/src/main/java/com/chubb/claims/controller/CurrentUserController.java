package com.chubb.claims.controller;

import com.chubb.platform.security.Actor;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Lets a client verify credentials and learn the caller's roles (the frontend's login step). */
@RestController
@Tag(name = "Current user")
public class CurrentUserController {

    @GetMapping("/api/me")
    @Operation(summary = "The authenticated user's username and roles")
    public Actor me(Authentication auth) {
        return Actor.from(auth);
    }
}
