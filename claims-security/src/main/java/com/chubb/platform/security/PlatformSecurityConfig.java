package com.chubb.platform.security;

import com.chubb.platform.security.SecurityUsersProperties.UserEntry;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.security.SecureRandom;
import java.util.Base64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;

/** Shared security plumbing. Each service imports this and declares its own URL access rules. */
@Configuration
@EnableConfigurationProperties(SecurityUsersProperties.class)
public class PlatformSecurityConfig {

    private static final Logger log = LoggerFactory.getLogger(PlatformSecurityConfig.class);

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    ProblemJsonSecurityHandlers problemJsonSecurityHandlers(ObjectMapper objectMapper) {
        return new ProblemJsonSecurityHandlers(objectMapper);
    }

    @Bean
    UserDetailsService userDetailsService(SecurityUsersProperties properties, PasswordEncoder encoder) {
        List<UserEntry> entries = properties.getUsers().isEmpty() ? demoUsers(demoPassword(properties)) : properties.getUsers();
        return new InMemoryUserDetailsManager(entries.stream()
                .map(u -> User.withUsername(u.getUsername()).password(encoder.encode(u.getPassword()))
                        .roles(u.getRole()).build())
                .toList());
    }

    private static String demoPassword(SecurityUsersProperties properties) {
        String configured = properties.getDemoPassword();
        if (configured != null && !configured.isBlank()) {
            return configured;
        }
        byte[] random = new byte[18];
        new SecureRandom().nextBytes(random);
        String generated = Base64.getUrlEncoder().withoutPadding().encodeToString(random);
        log.warn("app.security.demo-password is not set; generated password for the demo users: {}", generated);
        return generated;
    }

    /** Stateless HTTP Basic with problem+json 401/403 responses. */
    public static HttpSecurity baseline(HttpSecurity http, ProblemJsonSecurityHandlers handlers) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())   // stateless API, no cookies/sessions
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .httpBasic(b -> b.authenticationEntryPoint(handlers))
                .exceptionHandling(e -> e.authenticationEntryPoint(handlers).accessDeniedHandler(handlers))
                .anonymous(Customizer.withDefaults());
    }

    private static List<UserEntry> demoUsers(String password) {
        return List.of(
                new UserEntry("officer-1", password, Roles.OFFICER),
                new UserEntry("officer-2", password, Roles.OFFICER),
                new UserEntry("manager-1", password, Roles.MANAGER),
                new UserEntry("tan@example.com", password, Roles.CLAIMANT),
                new UserEntry("lee@example.com", password, Roles.CLAIMANT));
    }
}
