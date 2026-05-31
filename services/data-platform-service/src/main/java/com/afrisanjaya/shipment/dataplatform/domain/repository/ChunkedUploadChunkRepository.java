package com.afrisanjaya.shipment.dataplatform.domain.repository;

import com.afrisanjaya.shipment.dataplatform.domain.entity.ChunkedUpload;
import com.afrisanjaya.shipment.dataplatform.domain.entity.ChunkedUploadChunk;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ChunkedUploadChunkRepository extends JpaRepository<ChunkedUploadChunk, UUID> {

    long countByUpload(ChunkedUpload upload);

    Optional<ChunkedUploadChunk> findByUploadAndChunkIndex(ChunkedUpload upload, int chunkIndex);

    List<ChunkedUploadChunk> findByUploadOrderByChunkIndexAsc(ChunkedUpload upload);
}
