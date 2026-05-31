package com.afrisanjaya.shipment.logistics.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;

@Slf4j
@Component
public class DynamoDbInitializer {

    private final DynamoDbClient dynamoDbClient;

    public DynamoDbInitializer(DynamoDbClient dynamoDbClient) {
        this.dynamoDbClient = dynamoDbClient;
    }

    @Value("${dynamodb.endpoint:}")
    private String endpoint;

    @EventListener(ApplicationReadyEvent.class)
    public void createTablesIfMissing() {
        if (endpoint == null || endpoint.isBlank()) {
            log.info("[DYNAMODB] No local endpoint — tables provisioned by CDK, skipping init");
            return;
        }

        new Thread(() -> {
            int retries = 5;
            boolean connected = false;
            for (int i = 0; i < retries; i++) {
                try {
                    Thread.sleep(2000);
                    dynamoDbClient.listTables();
                    connected = true;
                    break;
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                } catch (Exception e) {
                    log.warn("[DYNAMODB] Connection attempt {}/{} failed: {}", i + 1, retries, e.getMessage());
                }
            }
            if (!connected) {
                log.error("[DYNAMODB] Failed to connect to DynamoDB after {} attempts. Skipping table creation.", retries);
                return;
            }
            log.info("[DYNAMODB] Checking tables...");
            createTable("sensor_readings",
                    KeySchemaElement.builder().attributeName("shipment_id").keyType(KeyType.HASH).build(),
                    KeySchemaElement.builder().attributeName("timestamp").keyType(KeyType.RANGE).build());
            createTable("shipment_tracking",
                    KeySchemaElement.builder().attributeName("shipment_id").keyType(KeyType.HASH).build(),
                    KeySchemaElement.builder().attributeName("timestamp").keyType(KeyType.RANGE).build());
            log.info("[DYNAMODB] Tables verified/created");
        }, "dynamodb-init").start();
    }

    private void createTable(String tableName, KeySchemaElement hashKey, KeySchemaElement rangeKey) {
        try {
            dynamoDbClient.describeTable(r -> r.tableName(tableName));
            log.info("[DYNAMODB] Table '{}' already exists", tableName);
        } catch (Exception e) {
            log.info("[DYNAMODB] Creating table '{}'...", tableName);
            try {
                dynamoDbClient.createTable(CreateTableRequest.builder()
                        .tableName(tableName)
                        .keySchema(hashKey, rangeKey)
                        .attributeDefinitions(
                                AttributeDefinition.builder()
                                        .attributeName(hashKey.attributeName())
                                        .attributeType(ScalarAttributeType.S).build(),
                                AttributeDefinition.builder()
                                        .attributeName(rangeKey.attributeName())
                                        .attributeType(ScalarAttributeType.S).build())
                        .billingMode(BillingMode.PAY_PER_REQUEST)
                        .build());
                log.info("[DYNAMODB] Table '{}' created successfully", tableName);
            } catch (Exception ce) {
                log.error("[DYNAMODB] Failed to create '{}': {}", tableName, ce.getMessage());
            }
        }
    }
}
