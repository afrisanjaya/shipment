package com.afrisanjaya.shipment.logistics.domain.repository;

import com.afrisanjaya.shipment.logistics.domain.entity.Shipment;
import com.afrisanjaya.shipment.logistics.domain.enums.ShipmentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ShipmentRepository extends JpaRepository<Shipment, UUID> {

    Optional<Shipment> findByIdempotencyKey(UUID idempotencyKey);

    Page<Shipment> findByUserId(UUID userId, Pageable pageable);

    Page<Shipment> findByUserIdAndStatus(UUID userId, ShipmentStatus status, Pageable pageable);

    Page<Shipment> findByTenantId(UUID tenantId, Pageable pageable);

    Page<Shipment> findByTenantIdAndStatus(UUID tenantId, ShipmentStatus status, Pageable pageable);
}
