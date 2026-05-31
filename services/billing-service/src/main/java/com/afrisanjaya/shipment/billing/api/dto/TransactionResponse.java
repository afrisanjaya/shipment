package com.afrisanjaya.shipment.billing.api.dto;

import com.afrisanjaya.shipment.billing.domain.enums.TransactionType;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record TransactionResponse(
    UUID transactionId,
    TransactionType type,
    BigDecimal amount,
    BigDecimal balanceBefore,
    BigDecimal balanceAfter,
    OffsetDateTime createdAt
) {}
