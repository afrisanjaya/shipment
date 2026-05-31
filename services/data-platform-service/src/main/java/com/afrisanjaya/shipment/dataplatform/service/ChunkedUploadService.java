package com.afrisanjaya.shipment.dataplatform.service;

import com.afrisanjaya.shipment.dataplatform.api.dto.ChunkUploadResponse;
import com.afrisanjaya.shipment.dataplatform.api.dto.ChunkedUploadInitRequest;
import com.afrisanjaya.shipment.dataplatform.api.dto.ChunkedUploadInitResponse;
import com.afrisanjaya.shipment.dataplatform.api.dto.ChunkedUploadStatusResponse;
import com.afrisanjaya.shipment.dataplatform.api.exception.TenantNotFoundException;
import com.afrisanjaya.shipment.dataplatform.domain.entity.ChunkedUpload;
import com.afrisanjaya.shipment.dataplatform.domain.entity.ChunkedUploadChunk;
import com.afrisanjaya.shipment.dataplatform.domain.entity.Tenant;
import com.afrisanjaya.shipment.dataplatform.domain.enums.UploadStatus;
import com.afrisanjaya.shipment.dataplatform.config.UploadStorageProperties;
import com.afrisanjaya.shipment.dataplatform.domain.repository.ChunkedUploadChunkRepository;
import com.afrisanjaya.shipment.dataplatform.domain.repository.ChunkedUploadRepository;
import com.afrisanjaya.shipment.dataplatform.domain.repository.TenantRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.kafka.core.KafkaTemplate;
import com.fasterxml.jackson.databind.ObjectMapper;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.util.Map;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChunkedUploadService {

    private final TenantRepository tenantRepository;
    private final ChunkedUploadRepository chunkedUploadRepository;
    private final ChunkedUploadChunkRepository chunkedUploadChunkRepository;
    private final UploadStorageProperties uploadStorageProperties;
    private final S3Client s3Client;
    private final String s3BucketName;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    @Transactional
    public ChunkedUploadInitResponse init(@Valid ChunkedUploadInitRequest request) {
        Tenant tenant = tenantRepository.findById(request.tenantId())
                .orElseThrow(() -> new TenantNotFoundException(request.tenantId()));

        ChunkedUpload upload = ChunkedUpload.builder()
                .tenantId(tenant.getId())
                .fileName(request.fileName())
                .contentType(request.contentType())
                .totalChunks(request.totalChunks())
                .receivedChunks(0)
                .totalSizeBytes(request.totalSizeBytes())
                .status(UploadStatus.INITIATED)
                .build();

        ChunkedUpload saved = chunkedUploadRepository.save(upload);
        return new ChunkedUploadInitResponse(saved.getId(), saved.getStatus(), saved.getTotalChunks(), saved.getReceivedChunks());
    }

    @Transactional
    public ChunkUploadResponse uploadChunk(UUID uploadId, int chunkIndex, MultipartFile chunkFile) {
        ChunkedUpload upload = chunkedUploadRepository.findById(uploadId)
                .orElseThrow(() -> new IllegalArgumentException("Upload not found: " + uploadId));

        if (upload.getStatus() == UploadStatus.COMPLETED) {
            throw new IllegalStateException("Upload already completed: " + uploadId);
        }

        byte[] chunkData = readChunk(chunkFile);
        ChunkedUploadChunk chunk = chunkedUploadChunkRepository.findByUploadAndChunkIndex(upload, chunkIndex)
                .map(existing -> updateChunk(existing, chunkData, chunkFile))
                .orElseGet(() -> createChunk(upload, chunkIndex, chunkData, chunkFile));

        chunkedUploadChunkRepository.save(chunk);

        int receivedChunks = Math.toIntExact(chunkedUploadChunkRepository.countByUpload(upload));
        upload.setReceivedChunks(receivedChunks);
        upload.setStatus(receivedChunks >= upload.getTotalChunks() ? UploadStatus.READY : UploadStatus.UPLOADING);
        chunkedUploadRepository.save(upload);

        return new ChunkUploadResponse(uploadId, chunkIndex, receivedChunks, upload.getTotalChunks(), upload.getStatus());
    }

    @Transactional(readOnly = true)
    public ChunkedUploadStatusResponse getStatus(UUID uploadId) {
        ChunkedUpload upload = chunkedUploadRepository.findById(uploadId)
                .orElseThrow(() -> new IllegalArgumentException("Upload not found: " + uploadId));

        return new ChunkedUploadStatusResponse(
                upload.getId(),
                upload.getTenantId(),
                upload.getFileName(),
                upload.getContentType(),
                upload.getTotalChunks(),
                upload.getReceivedChunks(),
                upload.getTotalSizeBytes(),
                upload.getStatus(),
                upload.getStoragePath(),
                upload.getCreatedAt(),
                upload.getUpdatedAt(),
                upload.getCompletedAt()
        );
    }

    @Transactional
    public ChunkedUploadStatusResponse complete(UUID uploadId) {
        ChunkedUpload upload = chunkedUploadRepository.findById(uploadId)
                .orElseThrow(() -> new IllegalArgumentException("Upload not found: " + uploadId));

        var chunks = chunkedUploadChunkRepository.findByUploadOrderByChunkIndexAsc(upload);
        if (chunks.size() != upload.getTotalChunks()) {
            throw new IllegalStateException("Upload is incomplete: received=" + chunks.size() + ", expected=" + upload.getTotalChunks());
        }

        byte[] assembled = assemble(chunks);

        String objectKey = uploadId.toString() + "_" + upload.getFileName();
        try {
            log.info("[UPLOAD] Uploading assembled file to S3: bucket={}, key={}, size={} bytes",
                    s3BucketName, objectKey, assembled.length);
            s3Client.putObject(
                    PutObjectRequest.builder()
                            .bucket(s3BucketName)
                            .key(objectKey)
                            .contentType(upload.getContentType())
                            .build(),
                    RequestBody.fromBytes(assembled));
            log.info("[UPLOAD] SUCCESS — File uploaded to S3: bucket={}, key={}, size={} bytes",
                    s3BucketName, objectKey, assembled.length);
        } catch (Exception e) {
            log.error("[UPLOAD] FAILED — S3 upload error for bucket={}, key={}: {}. File saved locally only.",
                    s3BucketName, objectKey, e.getMessage());
        }

        Path targetPath = Path.of(storageDir(), uploadId.toString(), upload.getFileName());
        try {
            Files.createDirectories(targetPath.getParent());
            Files.write(targetPath, assembled);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to assemble upload " + uploadId, e);
        }

        upload.setStatus(UploadStatus.COMPLETED);
        upload.setStoragePath(targetPath.toString());
        upload.setCompletedAt(OffsetDateTime.now(java.time.ZoneOffset.UTC));
        upload.setReceivedChunks(chunks.size());
        chunkedUploadRepository.save(upload);

        try {
            Map<String, Object> event = Map.of(
                    "uploadId", upload.getId().toString(),
                    "tenantId", upload.getTenantId().toString(),
                    "objectKey", objectKey,
                    "documentType", "INVOICE"
            );
            String message = objectMapper.writeValueAsString(event);
            log.info("[UPLOAD] Publishing document-uploaded event to Kafka: {}", message);
            kafkaTemplate.send("document-uploaded", message);
        } catch (Exception e) {
            log.warn("[UPLOAD] Failed to publish document-uploaded event to Kafka: {}", e.getMessage());
        }

        return getStatus(uploadId);
    }

    private ChunkedUploadChunk createChunk(ChunkedUpload upload, int chunkIndex, byte[] chunkData, MultipartFile chunkFile) {
        return ChunkedUploadChunk.builder()
                .upload(upload)
                .chunkIndex(chunkIndex)
                .chunkData(chunkData)
                .chunkSizeBytes(chunkData.length)
                .checksum(checksum(chunkData))
                .build();
    }

    private ChunkedUploadChunk updateChunk(ChunkedUploadChunk chunk, byte[] chunkData, MultipartFile chunkFile) {
        chunk.setChunkData(chunkData);
        chunk.setChunkSizeBytes(chunkData.length);
        chunk.setChecksum(checksum(chunkData));
        return chunk;
    }

    private byte[] readChunk(MultipartFile chunkFile) {
        try {
            return chunkFile.getBytes();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read upload chunk", e);
        }
    }

    private byte[] assemble(java.util.List<ChunkedUploadChunk> chunks) {
        return chunks.stream()
                .sorted(Comparator.comparingInt(ChunkedUploadChunk::getChunkIndex))
                .map(ChunkedUploadChunk::getChunkData)
                .collect(Collectors.collectingAndThen(
                        Collectors.toList(),
                        buffers -> {
                            int totalSize = buffers.stream().mapToInt(bytes -> bytes.length).sum();
                            byte[] assembled = new byte[totalSize];
                            int offset = 0;
                            for (byte[] buffer : buffers) {
                                System.arraycopy(buffer, 0, assembled, offset, buffer.length);
                                offset += buffer.length;
                            }
                            return assembled;
                        }
                ));
    }

    private String storageDir() {
        return uploadStorageProperties.storageDir() != null ? uploadStorageProperties.storageDir() : "./uploads";
    }

    private String checksum(byte[] chunkData) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(chunkData);
            StringBuilder builder = new StringBuilder(hash.length * 2);
            for (byte value : hash) {
                builder.append(String.format("%02x", value));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
