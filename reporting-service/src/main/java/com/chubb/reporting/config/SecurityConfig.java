package com.chubb.reporting.config;

import static com.chubb.platform.security.Roles.MANAGER;

import com.chubb.platform.security.PlatformSecurityConfig;
import com.chubb.platform.security.ProblemJsonSecurityHandlers;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;

/** Reports expose portfolio-wide figures, so every report endpoint is restricted to the MANAGER role. */
@Configuration
@EnableWebSecurity
@Import(PlatformSecurityConfig.class)
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, ProblemJsonSecurityHandlers handlers) throws Exception {
        PlatformSecurityConfig.baseline(http, handlers).authorizeHttpRequests(auth -> auth
                .requestMatchers(PlatformSecurityConfig.PUBLIC_ENDPOINTS).permitAll()
                .requestMatchers("/api/reports/**").hasRole(MANAGER)
                .anyRequest().denyAll());
        return http.build();
    }
}
