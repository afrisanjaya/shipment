package com.afrisanjaya.shipment.logistics.api.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record SkuResponse(
    UUID id,
    UUID warehouseId,
    String skuCode,
    String name,
    String category,
    BigDecimal unitPrice,
    int quantity,
    BigDecimal weightKg,
    boolean isActive
) {}
