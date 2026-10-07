package com.chubb.claims.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    OpenAPI claimsOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Claims Service API")
                        .version("0.1.0")
                        .description("""
                                System of record for motor and property claims: claimant submission and tracking, \
                                staff queue and decisions. Staff endpoints identify the acting officer with the \
                                X-Officer-Id header (authentication is out of scope). Errors use RFC 7807 problem \
                                details: 400 validation, 404 unknown claim, 409 invalid transition or concurrent \
                                update, 422 business-rule violation."""))
                .addServersItem(new Server().url("http://localhost:8081").description("Local"));
    }
}
