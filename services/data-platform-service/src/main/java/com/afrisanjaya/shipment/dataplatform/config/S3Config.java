package com.afrisanjaya.shipment.dataplatform.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

import java.net.URI;

@Slf4j
@Configuration
public class S3Config {

    @Value("${s3.bucket:Shipment-logistics-catalog}")
    private String bucketName;

    @Value("${s3.endpoint:}")
    private String endpoint;

    @Value("${s3.region}")
    private String region;

    @Value("${s3.access-key-id:}")
    private String accessKeyId;

    @Value("${s3.secret-access-key:}")
    private String secretAccessKey;

    @Bean
    public S3Client s3Client() {
        var builder = S3Client.builder()
                .region(Region.of(region));

        if (accessKeyId != null && !accessKeyId.isBlank()
                && secretAccessKey != null && !secretAccessKey.isBlank()) {
            builder.credentialsProvider(StaticCredentialsProvider.create(
                    AwsBasicCredentials.create(accessKeyId, secretAccessKey)));
        }

        if (endpoint != null && !endpoint.isBlank()) {
            log.info("[S3] Creating client — endpoint={}", endpoint);
            builder.endpointOverride(URI.create(endpoint));
            builder.forcePathStyle(true);
        } else {
            log.info("[S3] Creating client for region={} (default AWS endpoint), bucket={}", region, bucketName);
        }

        return builder.build();
    }

    @Bean
    public String s3BucketName() {
        return bucketName;
    }
}
