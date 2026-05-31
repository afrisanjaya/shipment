package com.afrisanjaya.shipment.dataplatform.api;

import com.afrisanjaya.shipment.dataplatform.api.dto.*;
import com.afrisanjaya.shipment.dataplatform.service.ChunkedUploadService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/documents/upload")
@RequiredArgsConstructor
@Tag(name = "Document Upload API", description = "Resumable chunked upload for document ingestion")
public class UploadController {

    private final ChunkedUploadService chunkedUploadService;

    @Operation(summary = "Initialize a resumable upload")
    @PostMapping("/init")
    public ResponseEntity<ChunkedUploadInitResponse> init(@Valid @RequestBody ChunkedUploadInitRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(chunkedUploadService.init(request));
    }

    @Operation(summary = "Upload one chunk of a resumable file")
    @PostMapping(value = "/chunk", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ChunkUploadResponse> uploadChunk(
            @RequestParam UUID uploadId,
            @RequestParam int chunkIndex,
            @RequestPart("chunk") MultipartFile chunk) {
        return ResponseEntity.ok(chunkedUploadService.uploadChunk(uploadId, chunkIndex, chunk));
    }

    @Operation(summary = "Get resumable upload status")
    @GetMapping("/status/{uploadId}")
    public ResponseEntity<ChunkedUploadStatusResponse> status(@PathVariable UUID uploadId) {
        return ResponseEntity.ok(chunkedUploadService.getStatus(uploadId));
    }

    @Operation(summary = "Complete a resumable upload")
    @PostMapping("/complete")
    public ResponseEntity<ChunkedUploadStatusResponse> complete(@Valid @RequestBody CompleteChunkedUploadRequest request) {
        return ResponseEntity.ok(chunkedUploadService.complete(request.uploadId()));
    }
}
