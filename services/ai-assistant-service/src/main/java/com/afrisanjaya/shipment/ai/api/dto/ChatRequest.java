package com.afrisanjaya.shipment.ai.api.dto;

import jakarta.validation.constraints.NotBlank;

public record ChatRequest(
    @NotBlank(message = "Message cannot be empty")
    String message,

    String sessionId,

    String tenantId
) {}
