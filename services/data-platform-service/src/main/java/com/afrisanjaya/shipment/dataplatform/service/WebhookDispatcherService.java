package com.afrisanjaya.shipment.dataplatform.service;

import com.afrisanjaya.shipment.dataplatform.domain.entity.WebhookDeliveryLog;
import com.afrisanjaya.shipment.dataplatform.domain.entity.WebhookSubscription;
import com.afrisanjaya.shipment.dataplatform.domain.repository.WebhookDeliveryLogRepository;
import com.afrisanjaya.shipment.dataplatform.domain.repository.WebhookSubscriptionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class WebhookDispatcherService {

    private final WebhookSubscriptionRepository subscriptionRepository;
    private final WebhookDeliveryLogRepository deliveryLogRepository;
    private final RestTemplate restTemplate;

    private static final int MAX_RETRY_ATTEMPTS = 3;
    private static final long[] RETRY_DELAY_SECONDS = {1, 4, 16};

    @Async
    public void dispatch(UUID tenantId, String eventType, Map<String, Object> payload) {
        List<WebhookSubscription> subscriptions =
                subscriptionRepository.findByTenantIdAndIsActiveTrue(tenantId);

        if (subscriptions.isEmpty()) {
            log.debug("No active webhook subscriptions for tenantId={}", tenantId);
            return;
        }

        for (WebhookSubscription sub : subscriptions) {
            if (!matchesEventType(sub.getEventTypes(), eventType)) {
                continue;
            }
            deliverWithRetry(sub, tenantId, eventType, payload);
        }
    }


    private void deliverWithRetry(WebhookSubscription sub, UUID tenantId,
                                   String eventType, Map<String, Object> payload) {
        WebhookDeliveryLog logEntry = createLogEntry(sub, tenantId, eventType, payload);

        for (int attempt = 1; attempt <= MAX_RETRY_ATTEMPTS; attempt++) {
            try {
                log.debug("Webhook attempt {}/{} → {}", attempt, MAX_RETRY_ATTEMPTS, sub.getCallbackUrl());

                restTemplate.postForEntity(sub.getCallbackUrl(), payload, String.class);

                logEntry.setDeliveryStatus("SUCCESS");
                logEntry.setAttemptCount(attempt);
                logEntry.setDeliveredAt(OffsetDateTime.now());
                deliveryLogRepository.save(logEntry);
                log.info("Webhook delivered: tenantId={} url={} attempt={}", tenantId, sub.getCallbackUrl(), attempt);
                return;

            } catch (Exception e) {
                String errorMsg = e.getMessage() != null
                        ? e.getMessage().substring(0, Math.min(e.getMessage().length(), 1900))
                        : "Unknown error";
                log.warn("Webhook delivery failed: tenantId={} url={} attempt={}/{} error={}",
                        tenantId, sub.getCallbackUrl(), attempt, MAX_RETRY_ATTEMPTS, errorMsg);

                logEntry.setAttemptCount(attempt);
                logEntry.setLastErrorMessage(errorMsg);

                if (attempt < MAX_RETRY_ATTEMPTS) {
                    long delaySec = RETRY_DELAY_SECONDS[attempt - 1];
                    logEntry.setNextRetryAt(OffsetDateTime.now().plusSeconds(delaySec));
                    deliveryLogRepository.save(logEntry);

                    try {
                        Thread.sleep(delaySec * 1000);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        logEntry.setDeliveryStatus("FAILED");
                        logEntry.setNextRetryAt(null);
                        deliveryLogRepository.save(logEntry);
                        return;
                    }
                }
            }
        }

        logEntry.setDeliveryStatus("FAILED");
        logEntry.setNextRetryAt(null);
        deliveryLogRepository.save(logEntry);
        log.error("Webhook FAILED after {} attempts: tenantId={} url={}",
                MAX_RETRY_ATTEMPTS, tenantId, sub.getCallbackUrl());
    }


    private WebhookDeliveryLog createLogEntry(
            WebhookSubscription sub, UUID tenantId, String eventType,
            Map<String, Object> payload) {
        WebhookDeliveryLog entry = WebhookDeliveryLog.builder()
                .subscription(sub)
                .tenantId(tenantId)
                .eventType(eventType)
                .payload(payload)
                .deliveryStatus("PENDING")
                .attemptCount(0)
                .maxAttempts(MAX_RETRY_ATTEMPTS)
                .build();
        return deliveryLogRepository.save(entry);
    }

    private boolean matchesEventType(String subscriptionEventTypes, String actualEventType) {
        if (subscriptionEventTypes == null || subscriptionEventTypes.isBlank()) {
            return true;
        }
        if ("*".equals(subscriptionEventTypes.trim())) {
            return true;
        }
        return Arrays.asList(subscriptionEventTypes.split("\\s*,\\s*"))
                .contains(actualEventType);
    }
}
