package com.afrisanjaya.shipment.logistics.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.afrisanjaya.shipment.logistics.domain.repository.DynamoDbTrackingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class GpsTrackingConsumer {

    private final DynamoDbTrackingRepository trackingRepository;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "shipment-gps-ping", groupId = "logistics-gps-consumer")
    public void consume(String message) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> ping = objectMapper.readValue(message, Map.class);
            String shipmentId = ping.get("shipmentId").toString();

            trackingRepository.save(shipmentId, ping);
            log.debug("[GPS] Stored ping for shipment={} lat={} lon={}",
                    shipmentId, ping.get("latitude"), ping.get("longitude"));
        } catch (Exception e) {
            log.error("[GPS] Failed to process GPS ping: {}", e.getMessage());
        }
    }
}
