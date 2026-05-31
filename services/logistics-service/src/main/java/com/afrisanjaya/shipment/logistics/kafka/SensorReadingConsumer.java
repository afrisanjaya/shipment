package com.afrisanjaya.shipment.logistics.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.afrisanjaya.shipment.logistics.domain.repository.DynamoDbSensorRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class SensorReadingConsumer {

    private final DynamoDbSensorRepository sensorRepository;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "sensor-reading", groupId = "logistics-sensor-consumer")
    public void consume(String message) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> reading = objectMapper.readValue(message, Map.class);
            String shipmentId = reading.get("shipmentId").toString();

            sensorRepository.save(shipmentId, reading);
            log.debug("[SENSOR] Stored reading for shipment={} type={}",
                    shipmentId, reading.get("sensorType"));
        } catch (Exception e) {
            log.error("[SENSOR] Failed to process sensor reading: {}", e.getMessage());
        }
    }
}
