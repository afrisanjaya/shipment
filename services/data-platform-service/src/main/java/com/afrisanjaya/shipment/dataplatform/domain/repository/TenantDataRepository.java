package com.afrisanjaya.shipment.dataplatform.domain.repository;

import com.afrisanjaya.shipment.dataplatform.domain.entity.TenantData;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.UUID;

@Repository
public interface TenantDataRepository extends JpaRepository<TenantData, UUID> {

    @Query(value = """
        SELECT * FROM tenant_data d
        WHERE d.tenant_id = :tenantId
          AND (:dataType IS NULL OR d.data_type = :dataType)
          AND (CAST(:from AS TIMESTAMPTZ) IS NULL OR d.created_at >= CAST(:from AS TIMESTAMPTZ))
          AND (CAST(:to AS TIMESTAMPTZ) IS NULL OR d.created_at <= CAST(:to AS TIMESTAMPTZ))
        ORDER BY d.created_at DESC
    """, nativeQuery = true)
    Page<TenantData> queryData(UUID tenantId, String dataType,
                                OffsetDateTime from, OffsetDateTime to,
                                Pageable pageable);
}
