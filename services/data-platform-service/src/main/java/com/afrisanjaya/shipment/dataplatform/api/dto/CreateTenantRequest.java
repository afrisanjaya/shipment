package com.afrisanjaya.shipment.dataplatform.api.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateTenantRequest(
    @NotBlank(message = "Tenant name is required")
    String name,

    String plan
) {}
