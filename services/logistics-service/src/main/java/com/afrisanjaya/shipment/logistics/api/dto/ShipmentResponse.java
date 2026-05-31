package com.afrisanjaya.shipment.logistics.api.dto;

import com.afrisanjaya.shipment.logistics.domain.enums.ShipmentStatus;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record ShipmentResponse(
    UUID id,
    UUID idempotencyKey,
    UUID tenantId,
    UUID userId,
    UUID skuId,
    UUID originWarehouseId,
    UUID destWarehouseId,
    ShipmentStatus status,
    int quantity,
    BigDecimal totalWeightKg,
    String priority,
    OffsetDateTime estimatedDeliveryAt,
    OffsetDateTime createdAt
) {}
