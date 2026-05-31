package com.afrisanjaya.shipment.billing.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "billing.sqs")
public record BillingSqsProperties(
        boolean enabled,
        String endpoint,
        String region,
        String accessKeyId,
        String secretAccessKey,
        String queueName,
        long pollDelayMs
) {
}
