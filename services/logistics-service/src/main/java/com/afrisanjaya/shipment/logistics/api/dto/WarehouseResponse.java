package com.afrisanjaya.shipment.logistics.api.dto;

import java.util.UUID;

public record WarehouseResponse(
    UUID id,
    UUID tenantId,
    String name,
    String location,
    String warehouseType,
    int capacity,
    String address
) {}
