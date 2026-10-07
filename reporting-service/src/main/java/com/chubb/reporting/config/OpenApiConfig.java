package com.chubb.reporting.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    OpenAPI reportingOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Reporting Service API")
                        .version("0.1.0")
                        .description("""
                                Manager-facing read API built from claims-service events on Kafka. Data is \
                                eventually consistent (typically about a second behind). Exposure is reported per \
                                currency; currencies are never summed together."""))
                .addServersItem(new Server().url("http://localhost:8082").description("Local"));
    }
}
