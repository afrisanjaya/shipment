package com.afrisanjaya.shipment.logistics.api.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.NOT_FOUND)
public class ShipmentNotFoundException extends RuntimeException {
    public ShipmentNotFoundException(java.util.UUID id) {
        super("Shipment not found: " + id);
    }
}
