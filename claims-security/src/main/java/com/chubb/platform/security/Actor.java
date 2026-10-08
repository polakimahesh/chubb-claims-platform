package com.chubb.platform.security;

import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.security.core.Authentication;

/** The authenticated caller: username (an officer id, or a claimant's e-mail) and roles. */
public record Actor(String username, Set<String> roles) {

    public static Actor from(Authentication authentication) {
        Set<String> roles = authentication.getAuthorities().stream()
                .map(a -> a.getAuthority().replaceFirst("^ROLE_", ""))
                .collect(Collectors.toSet());
        return new Actor(authentication.getName(), roles);
    }

    public boolean isClaimant() {
        return roles.contains(Roles.CLAIMANT);
    }

    public boolean isOfficer() {
        return roles.contains(Roles.OFFICER);
    }

    public boolean isManager() {
        return roles.contains(Roles.MANAGER);
    }
}
