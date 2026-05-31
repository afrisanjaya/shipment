package com.afrisanjaya.shipment.billing.api.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record TopUpRequest(
    @NotNull(message = "Amount is required")
    @Min(value = 10000, message = "Minimum top-up amount is 10,000")
    BigDecimal amount,

    String description
) {}
