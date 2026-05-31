package com.afrisanjaya.shipment.billing.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;
import software.amazon.awssdk.services.sqs.model.GetQueueUrlRequest;

import java.net.URI;

@Slf4j
@Configuration
@ConditionalOnProperty(prefix = "wallet.sqs", name = "enabled", havingValue = "true")
public class SqsConfig {

    @Bean
    public SqsAsyncClient sqsAsyncClient(BillingSqsProperties properties) {
        var credentials = StaticCredentialsProvider.create(
                AwsBasicCredentials.create(properties.accessKeyId(), properties.secretAccessKey()));

        var builder = SqsAsyncClient.builder()
                .region(Region.of(properties.region()))
                .credentialsProvider(credentials);

        if (properties.endpoint() != null && !properties.endpoint().isBlank()) {
            log.info("Creating SQS client for endpoint={}", properties.endpoint());
            builder.endpointOverride(URI.create(properties.endpoint()));
        } else {
            log.info("Creating SQS client for region={} (default AWS endpoint)", properties.region());
        }

        return builder.build();
    }

    @Bean
    public String walletTransactionQueueUrl(SqsAsyncClient sqsAsyncClient, BillingSqsProperties properties) {
        return sqsAsyncClient.getQueueUrl(GetQueueUrlRequest.builder()
                        .queueName(properties.queueName())
                        .build())
                .join()
                .queueUrl();
    }
}
