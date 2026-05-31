package com.afrisanjaya.shipment.billing.api.exception;

import java.util.UUID;

public class DuplicateIdempotencyKeyException extends RuntimeException {
    public DuplicateIdempotencyKeyException(UUID key) {
        super("Duplicate request with same idempotency key but different payload: " + key);
    }
}
