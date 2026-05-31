package com.afrisanjaya.shipment.dataplatform.api.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.NOT_FOUND)
public class TenantNotFoundException extends RuntimeException {
    public TenantNotFoundException(java.util.UUID id) {
        super("Tenant not found: " + id);
    }
    public TenantNotFoundException(String message) {
        super(message);
    }
}
