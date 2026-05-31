package com.afrisanjaya.shipment.dataplatform.api.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record CompleteChunkedUploadRequest(
        @NotNull UUID uploadId
) {
}
