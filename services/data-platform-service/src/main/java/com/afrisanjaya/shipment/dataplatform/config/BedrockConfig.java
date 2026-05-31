package com.afrisanjaya.shipment.dataplatform.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.bedrockagent.BedrockAgentClient;

import java.time.Duration;

@Slf4j
@Configuration
public class BedrockConfig {

    @Value("${bedrock.kb.id}")
    private String knowledgeBaseId;

    @Value("${s3.region}")
    private String region;

    @Bean
    public BedrockAgentClient bedrockAgentClient() {
        log.info("[BEDROCK] Creating BedrockAgentClient for region={}, kbId={}", region, knowledgeBaseId);
        return BedrockAgentClient.builder()
                .region(Region.of(region))
                .credentialsProvider(DefaultCredentialsProvider.create())
                .build();
    }
}
