package com.afrisanjaya.shipment.logistics.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.afrisanjaya.shipment.logistics.domain.entity.OutboxEvent;
import com.afrisanjaya.shipment.logistics.domain.entity.Shipment;
import com.afrisanjaya.shipment.logistics.domain.repository.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class OutboxService {

    private static final TypeReference<Map<String, Object>> PAYLOAD_TYPE = new TypeReference<>() {
    };

    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    public void publishEvent(Shipment shipment, String eventType) {
        try {
            Map<String, Object> payload = buildPayload(shipment);

            String topic = switch (eventType) {
                case "ShipmentCreated_v1"    -> "shipment-created";
                case "ShipmentDispatched_v1" -> "shipment-dispatched";
                case "ShipmentCancelled_v1"  -> "shipment-cancelled";
                case "ShipmentDelivered_v1"  -> "shipment-delivered";
                default                      -> "shipment-events";
            };

            OutboxEvent event = OutboxEvent.builder()
                    .aggregateType("Shipment")
                    .aggregateId(shipment.getId())
                    .eventType(eventType)
                    .topic(topic)
                    .payload(payload)
                    .build();

            outboxEventRepository.save(event);
            log.debug("Saved outbox event: aggregateId={}, eventType={}, topic={}",
                    shipment.getId(), eventType, topic);
        } catch (IllegalArgumentException e) {
            log.error("Failed to serialize outbox event payload", e);
            throw new RuntimeException("Serialization error for outbox event", e);
        }
    }

    private Map<String, Object> buildPayload(Shipment shipment) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("shipmentId", shipment.getId());
        payload.put("idempotencyKey", shipment.getIdempotencyKey());
        payload.put("tenantId", shipment.getTenantId());
        payload.put("userId", shipment.getUserId());
        payload.put("skuId", shipment.getSku().getId());
        payload.put("originWarehouseId", shipment.getOriginWarehouse().getId());
        payload.put("destWarehouseId", shipment.getDestWarehouse().getId());
        payload.put("status", shipment.getStatus());
        payload.put("quantity", shipment.getQuantity());
        payload.put("totalWeightKg", shipment.getTotalWeightKg());
        payload.put("priority", shipment.getPriority());
        payload.put("estimatedDeliveryAt", shipment.getEstimatedDeliveryAt());
        return objectMapper.convertValue(payload, PAYLOAD_TYPE);
    }
}
