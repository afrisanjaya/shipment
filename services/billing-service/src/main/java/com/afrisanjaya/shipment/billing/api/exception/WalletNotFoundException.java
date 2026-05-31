package com.afrisanjaya.shipment.billing.api.exception;

import java.util.UUID;

public class WalletNotFoundException extends RuntimeException {
    public WalletNotFoundException(UUID id) {
        super("Wallet not found for user/wallet ID: " + id);
    }
}
