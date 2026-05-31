package com.afrisanjaya.shipment.ai.api.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record ChatResponse(
    String sessionId,
    String response,
    ShipmentSummary shipment,
    List<String> citations,
    String source
) {
    public record ShipmentSummary(
        UUID shipmentId,
        String status,
        String originWarehouse,
        String destinationWarehouse,
        String skuName,
        int quantity,
        BigDecimal weightKg,
        OffsetDateTime estimatedDelivery,
        boolean slaAtRisk,
        String trackingUrl
    ) {}
}
