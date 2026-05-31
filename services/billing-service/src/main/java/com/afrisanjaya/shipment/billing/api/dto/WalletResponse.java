package com.afrisanjaya.shipment.billing.api.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record WalletResponse(
    UUID walletId,
    UUID userId,
    BigDecimal balance,
    String currency,
    OffsetDateTime updatedAt
) {}
