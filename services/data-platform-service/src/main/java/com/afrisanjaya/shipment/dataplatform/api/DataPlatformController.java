package com.afrisanjaya.shipment.dataplatform.api;

import com.afrisanjaya.shipment.dataplatform.api.dto.*;
import com.afrisanjaya.shipment.dataplatform.domain.entity.Tenant;
import com.afrisanjaya.shipment.dataplatform.domain.entity.WebhookDeliveryLog;
import com.afrisanjaya.shipment.dataplatform.domain.entity.WebhookSubscription;
import com.afrisanjaya.shipment.dataplatform.domain.repository.WebhookDeliveryLogRepository;
import com.afrisanjaya.shipment.dataplatform.domain.repository.WebhookSubscriptionRepository;
import com.afrisanjaya.shipment.dataplatform.service.DataIngestionService;
import com.afrisanjaya.shipment.dataplatform.service.TenantService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/tenants")
@RequiredArgsConstructor
@Tag(name = "Data Platform API", description = "Multi-tenant data ingestion & query platform")
public class DataPlatformController {

    private final TenantService tenantService;
    private final DataIngestionService dataIngestionService;
    private final WebhookSubscriptionRepository webhookSubscriptionRepository;
    private final WebhookDeliveryLogRepository webhookDeliveryLogRepository;

    @Operation(summary = "Create a new tenant with API key")
    @PostMapping
    public ResponseEntity<TenantResponse> createTenant(@Valid @RequestBody CreateTenantRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(tenantService.createTenant(request));
    }

    @Operation(summary = "Get tenant information")
    @GetMapping("/{tenantId}")
    public ResponseEntity<TenantResponse> getTenant(
            @PathVariable UUID tenantId,
            HttpServletRequest request) {
        Tenant tenant = (Tenant) request.getAttribute("tenant");
        if (tenant != null && !tenant.getId().equals(tenantId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        return ResponseEntity.ok(tenantService.getTenant(tenantId));
    }

    @Operation(summary = "Ingest JSON data for a tenant")
    @PostMapping("/{tenantId}/data")
    public ResponseEntity<DataQueryResponse> ingestData(
            @PathVariable UUID tenantId,
            @Valid @RequestBody IngestDataRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(dataIngestionService.ingest(tenantId, request));
    }

    @Operation(summary = "Query tenant data with filters")
    @GetMapping("/{tenantId}/data")
    public ResponseEntity<DataQueryResponse> queryData(
            @PathVariable UUID tenantId,
            @RequestParam(required = false) String dataType,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        return ResponseEntity.ok(
                dataIngestionService.query(tenantId, dataType, from, to, page, size));
    }


    @Operation(summary = "Create a webhook subscription for a tenant")
    @PostMapping("/{tenantId}/webhooks")
    public ResponseEntity<Map<String, Object>> createWebhook(
            @PathVariable UUID tenantId,
            @RequestBody Map<String, String> body,
            HttpServletRequest request) {

        String url = body.get("url");
        if (url == null || url.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "url is required"));
        }

        Tenant tenant = (Tenant) request.getAttribute("tenant");
        String eventTypes = body.getOrDefault("eventTypes", "data.ingested");
        String secret = body.get("secret");

        WebhookSubscription sub = WebhookSubscription.builder()
                .tenant(tenant)
                .callbackUrl(url)
                .eventTypes(eventTypes)
                .secret(secret)
                .build();

        webhookSubscriptionRepository.save(sub);

        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                "status", "created",
                "id", sub.getId().toString(),
                "tenantId", tenantId.toString(),
                "callbackUrl", url,
                "eventTypes", eventTypes
        ));
    }

    @Operation(summary = "List webhook subscriptions for a tenant")
    @GetMapping("/{tenantId}/webhooks")
    public ResponseEntity<List<Map<String, Object>>> listWebhooks(
            @PathVariable UUID tenantId) {

        List<WebhookSubscription> subs =
                webhookSubscriptionRepository.findByTenantId(tenantId);

        List<Map<String, Object>> result = subs.stream()
                .map(s -> Map.<String, Object>of(
                        "id", s.getId(),
                        "callbackUrl", s.getCallbackUrl(),
                        "eventTypes", s.getEventTypes(),
                        "isActive", s.isActive(),
                        "createdAt", s.getCreatedAt()
                ))
                .collect(Collectors.toList());

        return ResponseEntity.ok(result);
    }

    @Operation(summary = "Delete a webhook subscription")
    @DeleteMapping("/{tenantId}/webhooks/{subscriptionId}")
    public ResponseEntity<Void> deleteWebhook(
            @PathVariable UUID tenantId,
            @PathVariable UUID subscriptionId) {

        webhookSubscriptionRepository.deleteById(subscriptionId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Get webhook delivery logs for a tenant")
    @GetMapping("/{tenantId}/webhooks/logs")
    public ResponseEntity<List<Map<String, Object>>> webhookDeliveryLogs(
            @PathVariable UUID tenantId) {

        List<WebhookDeliveryLog> logs =
                webhookDeliveryLogRepository.findByTenantIdOrderByCreatedAtDesc(tenantId);

        List<Map<String, Object>> result = logs.stream()
                .map(l -> Map.<String, Object>of(
                        "id", l.getId(),
                        "eventType", l.getEventType(),
                        "deliveryStatus", l.getDeliveryStatus(),
                        "attemptCount", l.getAttemptCount(),
                        "lastResponseCode", l.getLastResponseCode() != null ? l.getLastResponseCode() : "N/A",
                        "lastErrorMessage", l.getLastErrorMessage() != null ? l.getLastErrorMessage() : "N/A",
                        "createdAt", l.getCreatedAt()
                ))
                .collect(Collectors.toList());

        return ResponseEntity.ok(result);
    }
}
