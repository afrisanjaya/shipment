package com.afrisanjaya.shipment.dataplatform.api.dto;

import com.afrisanjaya.shipment.dataplatform.domain.enums.UploadStatus;

import java.util.UUID;

public record ChunkUploadResponse(
        UUID uploadId,
        int chunkIndex,
        int receivedChunks,
        int totalChunks,
        UploadStatus status
) {
}
