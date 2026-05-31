package com.afrisanjaya.shipment.logistics.api.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.CONFLICT)
public class SkuNotAvailableException extends RuntimeException {
    public SkuNotAvailableException(java.util.UUID skuId, int requested, int available) {
        super("SKU " + skuId + " has insufficient stock: requested=" + requested + ", available=" + available);
    }
}
