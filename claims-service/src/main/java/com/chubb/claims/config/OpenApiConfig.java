package com.chubb.claims.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    private static final String BASIC = "basicAuth";

    @Bean
    OpenAPI claimsOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Claims Service API")
                        .version("0.2.0")
                        .description("""
                                System of record for motor and property claims: claimant submission and tracking, \
                                documents, staff queue and decisions. Authentication is HTTP Basic; roles are \
                                CLAIMANT (the e-mail is the username), OFFICER and MANAGER. Errors use RFC 7807 \
                                problem details: 400 validation, 401 not authenticated, 403 not permitted, 404 \
                                unknown claim, 409 invalid transition or concurrent update, 415 unsupported \
                                document type, 422 business-rule violation."""))
                .addServersItem(new Server().url("http://localhost:8081").description("Local"))
                .components(new Components().addSecuritySchemes(BASIC,
                        new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("basic")))
                .addSecurityItem(new SecurityRequirement().addList(BASIC));
    }
}
