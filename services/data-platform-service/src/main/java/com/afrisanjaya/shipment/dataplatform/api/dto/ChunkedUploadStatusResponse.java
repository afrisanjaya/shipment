package com.afrisanjaya.shipment.dataplatform.api.dto;

import com.afrisanjaya.shipment.dataplatform.domain.enums.UploadStatus;

import java.time.OffsetDateTime;
import java.util.UUID;

public record ChunkedUploadStatusResponse(
        UUID uploadId,
        UUID tenantId,
        String fileName,
        String contentType,
        int totalChunks,
        int receivedChunks,
        long totalSizeBytes,
        UploadStatus status,
        String storagePath,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        OffsetDateTime completedAt
) {
}
