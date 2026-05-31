package com.afrisanjaya.shipment.billing.messaging;

import com.afrisanjaya.shipment.billing.domain.enums.TransactionType;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record WalletTransactionMessage(
        UUID transactionId,
        UUID groupId,
        UUID deduplicationId,
        TransactionType transactionType,
        BigDecimal amount,
        UUID debitWalletId,
        UUID creditWalletId,
        BigDecimal debitBalanceBefore,
        BigDecimal creditBalanceBefore,
        UUID referenceId,
        String description,
        String currency,
        OffsetDateTime occurredAt
) {
}
