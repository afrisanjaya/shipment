package com.afrisanjaya.shipment.dataplatform.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.UUID;

public record ChunkedUploadInitRequest(
        @NotNull UUID tenantId,
        @NotBlank String fileName,
        @NotBlank String contentType,
        @Positive int totalChunks,
        @Positive long totalSizeBytes
) {
}
