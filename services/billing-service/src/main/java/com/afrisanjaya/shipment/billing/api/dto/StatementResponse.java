package com.afrisanjaya.shipment.billing.api.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record StatementResponse(
        UUID userId,
        UUID walletId,
        String month,
        BigDecimal openingBalance,
        BigDecimal closingBalance,
        BigDecimal totalDebits,
        BigDecimal totalCredits,
        List<TransactionResponse> transactions
) {}
