package com.afrisanjaya.shipment.logistics.domain.repository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Repository
@RequiredArgsConstructor
public class DynamoDbTrackingRepository {

    private final DynamoDbClient dynamoDbClient;
    private static final String TABLE = "shipment_tracking";

    public void save(String shipmentId, Map<String, Object> ping) {
        Map<String, AttributeValue> item = new LinkedHashMap<>();
        item.put("shipment_id", AttributeValue.builder().s(shipmentId).build());
        item.put("timestamp", AttributeValue.builder().s(requiredValue(ping, "timestamp")).build());
        item.put("latitude", AttributeValue.builder().n(ping.get("latitude").toString()).build());
        item.put("longitude", AttributeValue.builder().n(ping.get("longitude").toString()).build());
        putNumberIfPresent(item, "speed_kph", ping.get("speedKph"));
        putNumberIfPresent(item, "heading", ping.get("heading"));
        putNumberIfPresent(item, "accuracy", ping.get("accuracy"));
        if (ping.get("tenantId") != null) {
            item.put("tenant_id", AttributeValue.builder().s(ping.get("tenantId").toString()).build());
        }

        dynamoDbClient.putItem(PutItemRequest.builder()
                .tableName(TABLE)
                .item(item)
                .build());
    }

    public List<Map<String, Object>> findByShipmentId(String shipmentId, int limit) {
        var result = dynamoDbClient.query(QueryRequest.builder()
                .tableName(TABLE)
                .keyConditionExpression("shipment_id = :sid")
                .expressionAttributeValues(Map.of(
                        ":sid", AttributeValue.builder().s(shipmentId).build()))
                .scanIndexForward(false)
                .limit(limit)
                .build());

        return result.items().stream()
                .map(this::toMap)
                .collect(Collectors.toList());
    }

    private Map<String, Object> toMap(Map<String, AttributeValue> item) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("shipmentId", item.get("shipment_id").s());
        map.put("timestamp", item.get("timestamp").s());
        map.put("latitude", Double.parseDouble(item.get("latitude").n()));
        map.put("longitude", Double.parseDouble(item.get("longitude").n()));
        map.put("speedKph", item.containsKey("speed_kph") ? Double.parseDouble(item.get("speed_kph").n()) : null);
        map.put("heading", item.containsKey("heading") ? Double.parseDouble(item.get("heading").n()) : null);
        map.put("accuracy", item.containsKey("accuracy") ? Double.parseDouble(item.get("accuracy").n()) : null);
        return map;
    }

    private void putNumberIfPresent(Map<String, AttributeValue> item, String attributeName, Object value) {
        if (value != null) {
            item.put(attributeName, AttributeValue.builder().n(value.toString()).build());
        }
    }

    private String requiredValue(Map<String, Object> ping, String fieldName) {
        Object value = ping.get(fieldName);
        if (value == null) {
            throw new IllegalArgumentException("GPS ping field is required: " + fieldName);
        }
        return value.toString();
    }
}
