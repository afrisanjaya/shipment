package com.afrisanjaya.shipment.dataplatform.api.dto;

import com.afrisanjaya.shipment.dataplatform.domain.enums.UploadStatus;

import java.util.UUID;

public record ChunkedUploadInitResponse(
        UUID uploadId,
        UploadStatus status,
        int totalChunks,
        int receivedChunks
) {
}
