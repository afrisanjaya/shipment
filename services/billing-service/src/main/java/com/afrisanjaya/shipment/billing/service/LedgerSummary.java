package com.afrisanjaya.shipment.billing.service;

import java.math.BigDecimal;

public record LedgerSummary(BigDecimal totalDebits, BigDecimal totalCredits) {

    public static LedgerSummary empty() {
        return new LedgerSummary(BigDecimal.ZERO, BigDecimal.ZERO);
    }

    public BigDecimal difference() {
        return totalDebits.subtract(totalCredits).abs();
    }

    public boolean isBalanced() {
        return totalDebits.compareTo(totalCredits) == 0;
    }
}
