package com.afrisanjaya.shipment.logistics.domain.repository;

import com.afrisanjaya.shipment.logistics.domain.entity.Sku;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface SkuRepository extends JpaRepository<Sku, UUID> {

    List<Sku> findByWarehouseIdAndIsActiveTrue(UUID warehouseId);

    @Query("SELECT s FROM Sku s WHERE s.warehouse.id = :warehouseId AND s.quantity > 0 AND s.isActive = true")
    List<Sku> findAvailableSkus(UUID warehouseId);

    List<Sku> findByTenantId(UUID tenantId);
}
