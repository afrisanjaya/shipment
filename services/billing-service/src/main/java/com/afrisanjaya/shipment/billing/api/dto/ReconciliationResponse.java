package com.afrisanjaya.shipment.billing.api.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ReconciliationResponse(
        LocalDate date,
        BigDecimal totalDebits,
        BigDecimal totalCredits,
        boolean isBalanced,
        BigDecimal difference
) {}
