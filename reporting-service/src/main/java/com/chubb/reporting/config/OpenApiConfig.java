package com.chubb.reporting.config;

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
    OpenAPI reportingOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Reporting Service API")
                        .version("0.2.0")
                        .description("""
                                Manager-facing read API (role MANAGER, HTTP Basic) built from claims-service events \
                                on Kafka. Data is eventually consistent (typically about a second behind). Exposure \
                                is reported per currency; the /exposure/total endpoint converts using static \
                                indicative rates only."""))
                .addServersItem(new Server().url("http://localhost:8082").description("Local"))
                .components(new Components().addSecuritySchemes(BASIC,
                        new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("basic")))
                .addSecurityItem(new SecurityRequirement().addList(BASIC));
    }
}
