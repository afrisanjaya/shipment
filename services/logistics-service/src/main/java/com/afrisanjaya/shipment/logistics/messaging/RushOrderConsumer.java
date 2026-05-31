package com.afrisanjaya.shipment.logistics.messaging;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.afrisanjaya.shipment.logistics.service.ShipmentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class RushOrderConsumer {

    private final ShipmentService shipmentService;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "rush-order-results", groupId = "logistics-rush-order")
    public void consume(String message) {
        log.info("[RUSH-ORDER] Received result: {}",
                message.length() > 200 ? message.substring(0, 200) + "..." : message);

        try {
            JsonNode json = objectMapper.readTree(message);
            String status = json.path("status").asText();
            String saleId = json.path("sale_id").asText();
            String userId = json.path("user_id").asText();
            String skuId = json.path("sku_id").asText();

            if (!"SUCCESS".equals(status)) {
                log.info("[RUSH-ORDER] Skipping non-success result: sale={} user={} status={}",
                        saleId, userId, status);
                return;
            }

            if (saleId.isEmpty() || userId.isEmpty() || skuId.isEmpty()) {
                log.warn("[RUSH-ORDER] Missing required fields: sale={} user={} sku={}",
                        saleId, userId, skuId);
                return;
            }

            UUID userUuid = UUID.fromString(userId);
            UUID skuUuid = UUID.fromString(skuId);

            var request = new com.afrisanjaya.shipment.logistics.api.dto.CreateShipmentRequest(
                    userUuid, userUuid, skuUuid,
                    UUID.randomUUID(), UUID.randomUUID(), 1, "EXPRESS");
            var idempotencyKey = UUID.nameUUIDFromBytes(
                    ("rush-order:" + saleId + ":" + userId).getBytes());
            var shipment = shipmentService.createShipment(request, idempotencyKey);
            log.info("[RUSH-ORDER] Shipment created: id={} for sale={} user={}",
                    shipment.id(), saleId, userId);

        } catch (Exception e) {
            log.error("[RUSH-ORDER] Failed to process result: {}", e.getMessage());
            throw new RuntimeException("Rush order processing failed — will retry", e);
        }
    }
}
