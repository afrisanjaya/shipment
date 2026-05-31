package com.afrisanjaya.shipment.logistics.api.dto;

import com.afrisanjaya.shipment.logistics.domain.enums.ShipmentStatus;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record CreateShipmentRequest(
    @NotNull(message = "Tenant ID is required")
    UUID tenantId,

    @NotNull(message = "User ID is required")
    UUID userId,

    @NotNull(message = "SKU ID is required")
    UUID skuId,

    @NotNull(message = "Origin warehouse ID is required")
    UUID originWarehouseId,

    @NotNull(message = "Destination warehouse ID is required")
    UUID destWarehouseId,

    @NotNull(message = "Quantity is required")
    Integer quantity,

    String priority
) {
    public CreateShipmentRequest {
        if (priority == null || priority.isBlank()) {
            priority = "STANDARD";
        }
        if (quantity != null && quantity < 1) {
            throw new IllegalArgumentException("Quantity must be at least 1");
        }
    }
}
