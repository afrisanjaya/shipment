package com.afrisanjaya.shipment.logistics.api.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.NOT_FOUND)
public class WarehouseNotFoundException extends RuntimeException {
    public WarehouseNotFoundException(java.util.UUID id) {
        super("Warehouse not found: " + id);
    }
}
