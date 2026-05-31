package com.afrisanjaya.shipment.dataplatform.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.afrisanjaya.shipment.dataplatform.api.dto.ChunkUploadResponse;
import com.afrisanjaya.shipment.dataplatform.api.dto.ChunkedUploadInitRequest;
import com.afrisanjaya.shipment.dataplatform.api.dto.ChunkedUploadInitResponse;
import com.afrisanjaya.shipment.dataplatform.api.dto.ChunkedUploadStatusResponse;
import com.afrisanjaya.shipment.dataplatform.api.exception.TenantNotFoundException;
import com.afrisanjaya.shipment.dataplatform.config.UploadStorageProperties;
import com.afrisanjaya.shipment.dataplatform.domain.entity.ChunkedUpload;
import com.afrisanjaya.shipment.dataplatform.domain.entity.ChunkedUploadChunk;
import com.afrisanjaya.shipment.dataplatform.domain.entity.Tenant;
import com.afrisanjaya.shipment.dataplatform.domain.enums.UploadStatus;
import com.afrisanjaya.shipment.dataplatform.domain.repository.ChunkedUploadChunkRepository;
import com.afrisanjaya.shipment.dataplatform.domain.repository.ChunkedUploadRepository;
import com.afrisanjaya.shipment.dataplatform.domain.repository.TenantRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.mock.web.MockMultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ChunkedUploadServiceTest {

    @Mock
    private TenantRepository tenantRepository;

    @Mock
    private ChunkedUploadRepository chunkedUploadRepository;

    @Mock
    private ChunkedUploadChunkRepository chunkedUploadChunkRepository;

    @Mock
    private UploadStorageProperties uploadStorageProperties;

    @Mock
    private S3Client s3Client;

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private ChunkedUploadService chunkedUploadService;

    @Test
    void init_whenTenantNotFound_thenThrowsTenantNotFoundException() {
        UUID tenantId = UUID.randomUUID();
        ChunkedUploadInitRequest request = new ChunkedUploadInitRequest(tenantId, "test.pdf", "application/pdf", 3, 1024L);
        given(tenantRepository.findById(tenantId)).willReturn(Optional.empty());

        assertThatThrownBy(() -> chunkedUploadService.init(request))
                .isInstanceOf(TenantNotFoundException.class);
    }

    @Test
    void init_whenTenantExists_thenSavesChunkedUpload() {
        UUID tenantId = UUID.randomUUID();
        Tenant tenant = Tenant.builder().name("Acme").apiKey("tp_key").plan("PRO").build();
        tenant.setId(tenantId);

        ChunkedUploadInitRequest request = new ChunkedUploadInitRequest(tenantId, "test.pdf", "application/pdf", 3, 1024L);
        given(tenantRepository.findById(tenantId)).willReturn(Optional.of(tenant));

        ArgumentCaptor<ChunkedUpload> captor = ArgumentCaptor.forClass(ChunkedUpload.class);
        given(chunkedUploadRepository.save(captor.capture())).willAnswer(inv -> {
            ChunkedUpload u = inv.getArgument(0);
            u.setId(UUID.randomUUID());
            return u;
        });

        ChunkedUploadInitResponse response = chunkedUploadService.init(request);

        assertThat(response.uploadId()).isNotNull();
        assertThat(response.status()).isEqualTo(UploadStatus.INITIATED);
        assertThat(captor.getValue().getFileName()).isEqualTo("test.pdf");
    }

    @Test
    void uploadChunk_whenUploadNotFound_thenThrowsIllegalArgumentException() {
        UUID uploadId = UUID.randomUUID();
        MockMultipartFile file = new MockMultipartFile("chunk", "data".getBytes());
        given(chunkedUploadRepository.findById(uploadId)).willReturn(Optional.empty());

        assertThatThrownBy(() -> chunkedUploadService.uploadChunk(uploadId, 0, file))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Upload not found");
    }

    @Test
    void uploadChunk_whenCompleted_thenThrowsIllegalStateException() {
        UUID uploadId = UUID.randomUUID();
        ChunkedUpload upload = ChunkedUpload.builder().status(UploadStatus.COMPLETED).build();
        MockMultipartFile file = new MockMultipartFile("chunk", "data".getBytes());
        given(chunkedUploadRepository.findById(uploadId)).willReturn(Optional.of(upload));

        assertThatThrownBy(() -> chunkedUploadService.uploadChunk(uploadId, 0, file))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Upload already completed");
    }

    @Test
    void uploadChunk_whenValid_thenSavesChunkAndUpdatesReceivedCount() {
        UUID uploadId = UUID.randomUUID();
        ChunkedUpload upload = ChunkedUpload.builder()
                .status(UploadStatus.INITIATED)
                .totalChunks(3)
                .build();
        upload.setId(uploadId);

        MockMultipartFile file = new MockMultipartFile("chunk", "chunk1-data".getBytes());
        given(chunkedUploadRepository.findById(uploadId)).willReturn(Optional.of(upload));
        given(chunkedUploadChunkRepository.findByUploadAndChunkIndex(upload, 0)).willReturn(Optional.empty());
        given(chunkedUploadChunkRepository.countByUpload(upload)).willReturn(1L);

        ChunkUploadResponse response = chunkedUploadService.uploadChunk(uploadId, 0, file);

        assertThat(response.uploadId()).isEqualTo(uploadId);
        assertThat(response.receivedChunks()).isEqualTo(1);
        assertThat(response.status()).isEqualTo(UploadStatus.UPLOADING);
        verify(chunkedUploadChunkRepository).save(any(ChunkedUploadChunk.class));
    }

    @Test
    void complete_whenIncomplete_thenThrowsIllegalStateException() {
        UUID uploadId = UUID.randomUUID();
        ChunkedUpload upload = ChunkedUpload.builder().totalChunks(3).build();
        given(chunkedUploadRepository.findById(uploadId)).willReturn(Optional.of(upload));
        given(chunkedUploadChunkRepository.findByUploadOrderByChunkIndexAsc(upload)).willReturn(List.of());

        assertThatThrownBy(() -> chunkedUploadService.complete(uploadId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Upload is incomplete");
    }

    @Test
    void complete_whenValid_thenAssemblesFileAndUploadsToMinIOAndPublishesToKafka() throws Exception {
        UUID uploadId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        ChunkedUpload upload = ChunkedUpload.builder()
                .tenantId(tenantId)
                .fileName("test.pdf")
                .contentType("application/pdf")
                .totalChunks(2)
                .status(UploadStatus.READY)
                .build();
        upload.setId(uploadId);

        ChunkedUploadChunk c0 = ChunkedUploadChunk.builder().chunkIndex(0).chunkData("part1".getBytes()).build();
        ChunkedUploadChunk c1 = ChunkedUploadChunk.builder().chunkIndex(1).chunkData("part2".getBytes()).build();

        given(chunkedUploadRepository.findById(uploadId)).willReturn(Optional.of(upload));
        given(chunkedUploadChunkRepository.findByUploadOrderByChunkIndexAsc(upload)).willReturn(List.of(c0, c1));
        given(uploadStorageProperties.storageDir()).willReturn("target/test-uploads");
        given(objectMapper.writeValueAsString(any())).willReturn("mock-json");

        Path testFile = Path.of("target/test-uploads", uploadId.toString(), "test.pdf");
        Files.deleteIfExists(testFile);

        ChunkedUploadStatusResponse response = chunkedUploadService.complete(uploadId);

        assertThat(response.status()).isEqualTo(UploadStatus.COMPLETED);
        assertThat(Files.exists(testFile)).isTrue();
        assertThat(Files.readAllBytes(testFile)).isEqualTo("part1part2".getBytes());

        ArgumentCaptor<PutObjectRequest> putRequestCaptor = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(s3Client).putObject(putRequestCaptor.capture(), any(RequestBody.class));
        assertThat(putRequestCaptor.getValue().key()).isEqualTo(uploadId + "_test.pdf");
        assertThat(putRequestCaptor.getValue().contentType()).isEqualTo("application/pdf");

        verify(kafkaTemplate).send(eq("document-uploaded"), eq("mock-json"));
    }
}
