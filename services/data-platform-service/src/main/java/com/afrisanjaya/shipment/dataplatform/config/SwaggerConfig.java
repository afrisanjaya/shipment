package com.afrisanjaya.shipment.dataplatform.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SwaggerConfig {

    @Bean
    public OpenAPI dataPlatformOpenAPI() {
        return new OpenAPI()
                .info(new Info().title("Data Platform Service API")
                        .description("Shipment Data Platform — Multi-tenant JSONB data ingestion")
                        .version("v1.0.0"));
    }
}
