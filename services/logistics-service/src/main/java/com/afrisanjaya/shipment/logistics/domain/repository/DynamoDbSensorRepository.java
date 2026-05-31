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
public class DynamoDbSensorRepository {

    private final DynamoDbClient dynamoDbClient;
    private static final String TABLE = "sensor_readings";

    public void save(String shipmentId, Map<String, Object> reading) {
        Map<String, AttributeValue> item = new LinkedHashMap<>();
        item.put("shipment_id", AttributeValue.builder().s(shipmentId).build());
        item.put("timestamp", AttributeValue.builder().s(requiredValue(reading, "timestamp")).build());
        item.put("sensor_type", AttributeValue.builder().s(requiredValue(reading, "sensorType")).build());
        item.put("value", AttributeValue.builder().n(reading.get("value").toString()).build());
        item.put("unit", AttributeValue.builder().s(requiredValue(reading, "unit")).build());
        item.put("is_in_range", AttributeValue.builder().bool(Boolean.TRUE.equals(reading.get("isInRange"))).build());

        if (reading.get("thresholdMin") != null) {
            item.put("threshold_min", AttributeValue.builder().n(reading.get("thresholdMin").toString()).build());
        }
        if (reading.get("thresholdMax") != null) {
            item.put("threshold_max", AttributeValue.builder().n(reading.get("thresholdMax").toString()).build());
        }

        dynamoDbClient.putItem(PutItemRequest.builder()
                .tableName(TABLE)
                .item(item)
                .build());
    }

    public List<Map<String, Object>> findByShipmentId(String shipmentId, String sensorType) {
        boolean filterBySensorType = sensorType != null && !sensorType.isBlank();
        var builder = QueryRequest.builder()
                .tableName(TABLE)
                .keyConditionExpression("shipment_id = :sid")
                .expressionAttributeValues(filterBySensorType
                        ? Map.of(":sid", AttributeValue.builder().s(shipmentId).build(),
                        ":stype", AttributeValue.builder().s(sensorType).build())
                        : Map.of(":sid", AttributeValue.builder().s(shipmentId).build()))
                .scanIndexForward(false)
                .limit(100);

        if (filterBySensorType) {
            builder.filterExpression("sensor_type = :stype");
        }

        var result = dynamoDbClient.query(builder.build());

        return result.items().stream()
                .map(item -> {
                    Map<String, Object> map = new LinkedHashMap<>();
                    map.put("shipmentId", item.get("shipment_id").s());
                    map.put("timestamp", item.get("timestamp").s());
                    map.put("sensorType", item.containsKey("sensor_type") ? item.get("sensor_type").s() : "");
                    map.put("value", Double.parseDouble(item.get("value").n()));
                    map.put("unit", item.get("unit").s());
                    map.put("thresholdMin", item.containsKey("threshold_min")
                            ? Double.parseDouble(item.get("threshold_min").n()) : null);
                    map.put("thresholdMax", item.containsKey("threshold_max")
                            ? Double.parseDouble(item.get("threshold_max").n()) : null);
                    map.put("isInRange", item.containsKey("is_in_range") && item.get("is_in_range").bool());
                    return map;
                }).collect(Collectors.toList());
    }

    private String requiredValue(Map<String, Object> reading, String fieldName) {
        Object value = reading.get(fieldName);
        if (value == null) {
            throw new IllegalArgumentException("Sensor reading field is required: " + fieldName);
        }
        return value.toString();
    }
}
