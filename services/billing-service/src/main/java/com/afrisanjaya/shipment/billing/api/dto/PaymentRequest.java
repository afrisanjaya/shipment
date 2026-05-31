package com.afrisanjaya.shipment.billing.api.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.UUID;

public record PaymentRequest(
    @NotNull(message = "Booking ID is required")
    UUID bookingId,

    @NotNull(message = "Amount is required")
    @Min(value = 1, message = "Amount must be greater than 0")
    BigDecimal amount
) {}
