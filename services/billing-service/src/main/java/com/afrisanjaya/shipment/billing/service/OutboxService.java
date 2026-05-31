package com.afrisanjaya.shipment.billing.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.afrisanjaya.shipment.billing.domain.entity.OutboxEvent;
import com.afrisanjaya.shipment.billing.domain.repository.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class OutboxService {

    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    public void publishEvent(Object payloadObj, UUID aggregateId, String eventType) {
        try {
            Map<String, Object> payload = objectMapper.convertValue(payloadObj, new TypeReference<>() {});

            String topic = switch (eventType) {
                case "WalletToppedUp_v1" -> "wallet-topped-up";
                case "PaymentCompleted_v1" -> "payment-completed";
                case "PaymentFailed_v1" -> "payment-failed";
                case "RefundProcessed_v1" -> "refund-processed";
                default -> "wallet-events";
            };

            OutboxEvent event = OutboxEvent.builder()
                    .aggregateType(topic)
                    .aggregateId(aggregateId)
                    .eventType(eventType)
                    .payload(payload)
                    .build();

            outboxEventRepository.save(event);
            log.debug("Saved outbox event: aggregateId={}, eventType={}, topic={}", aggregateId, eventType, topic);
        } catch (IllegalArgumentException e) {
            log.error("Failed to serialize outbox event payload", e);
            throw new RuntimeException("Serialization error for outbox event", e);
        }
    }
}
