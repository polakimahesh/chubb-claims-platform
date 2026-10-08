package com.chubb.notification.config;

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
    OpenAPI notificationOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Notification Service API")
                        .version("0.2.0")
                        .description("""
                                Sends claimants an e-mail when their claim is received, picked up, needs information, \
                                or is decided. Built from claims-service events on Kafka (idempotent per event). This \
                                API lets claimants (and staff) read what was sent. The e-mail channel is simulated by a \
                                log-only sender behind the NotificationSender interface."""))
                .addServersItem(new Server().url("http://localhost:8083").description("Local"))
                .components(new Components().addSecuritySchemes(BASIC,
                        new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("basic")))
                .addSecurityItem(new SecurityRequirement().addList(BASIC));
    }
}
