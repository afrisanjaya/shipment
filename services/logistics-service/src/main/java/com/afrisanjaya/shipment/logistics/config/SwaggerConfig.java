package com.afrisanjaya.shipment.logistics.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SwaggerConfig {

    @Bean
    public OpenAPI logisticsOpenAPI() {
        return new OpenAPI()
                .info(new Info().title("Logistics Service API")
                        .description("Shipment Logistics Service — Supply Chain & Shipment Management")
                        .version("v1.0.0"));
    }
}
