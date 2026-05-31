package com.afrisanjaya.shipment.billing.api.exception;

import java.math.BigDecimal;
import java.util.UUID;

public class InsufficientBalanceException extends RuntimeException {
    public InsufficientBalanceException(UUID walletId, BigDecimal required, BigDecimal available) {
        super("Wallet %s has %.2f but requires %.2f".formatted(walletId, available, required));
    }
}
