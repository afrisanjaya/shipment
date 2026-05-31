package com.afrisanjaya.shipment.logistics.domain.repository;

import com.afrisanjaya.shipment.logistics.domain.entity.Warehouse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface WarehouseRepository extends JpaRepository<Warehouse, UUID> {

    Page<Warehouse> findByLocationContainingIgnoreCase(String location, Pageable pageable);

    Page<Warehouse> findByLocationContainingIgnoreCaseAndWarehouseType(
            String location, String warehouseType, Pageable pageable);

    Page<Warehouse> findByTenantId(UUID tenantId, Pageable pageable);
}
