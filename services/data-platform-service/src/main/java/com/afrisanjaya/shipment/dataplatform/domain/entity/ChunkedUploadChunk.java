package com.afrisanjaya.shipment.dataplatform.domain.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(
        name = "chunked_upload_chunks",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_chunked_upload_chunks_upload_chunk_index",
                columnNames = {"upload_id", "chunk_index"}
        )
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChunkedUploadChunk {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "upload_id", nullable = false)
    private ChunkedUpload upload;

    @Column(name = "chunk_index", nullable = false)
    private int chunkIndex;

    @Column(name = "chunk_data", nullable = false, columnDefinition = "BYTEA")
    private byte[] chunkData;

    @Column(name = "chunk_size_bytes", nullable = false)
    private int chunkSizeBytes;

    @Column(name = "checksum", length = 128)
    private String checksum;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    protected void prePersist() {
        createdAt = OffsetDateTime.now(java.time.ZoneOffset.UTC);
    }
}
