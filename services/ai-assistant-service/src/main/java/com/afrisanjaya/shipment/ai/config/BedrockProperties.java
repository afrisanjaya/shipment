package com.afrisanjaya.shipment.ai.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "bedrock")
public record BedrockProperties(
        Agent agent,
        Kb kb,
        S3vector s3vector
) {
    public record Agent(String id, String aliasId) {}
    public record Kb(String id) {}
    public record S3vector(String bucketArn, String region) {}
}
