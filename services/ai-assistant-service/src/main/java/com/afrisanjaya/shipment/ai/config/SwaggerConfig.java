package com.afrisanjaya.shipment.ai.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SwaggerConfig {

    @Bean
    public OpenAPI aiAssistantOpenAPI() {
        return new OpenAPI()
                .info(new Info().title("AI Assistant Service API")
                        .description("Shipment AI Assistant — Supply Chain & Logistics (Bedrock + S3 Vector)")
                        .version("v1.0.0"));
    }
}
