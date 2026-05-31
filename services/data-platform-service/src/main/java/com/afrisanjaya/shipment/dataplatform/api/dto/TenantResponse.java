package com.afrisanjaya.shipment.dataplatform.api.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record TenantResponse(
    UUID id,
    String name,
    String apiKey,
    String plan,
    boolean isActive,
    OffsetDateTime createdAt
) {}
