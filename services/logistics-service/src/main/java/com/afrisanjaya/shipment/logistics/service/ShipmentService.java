package com.afrisanjaya.shipment.logistics.service;

import com.afrisanjaya.shipment.logistics.api.dto.CreateShipmentRequest;
import com.afrisanjaya.shipment.logistics.api.dto.ShipmentResponse;
import com.afrisanjaya.shipment.logistics.api.exception.ShipmentNotFoundException;
import com.afrisanjaya.shipment.logistics.api.exception.WarehouseNotFoundException;
import com.afrisanjaya.shipment.logistics.api.exception.SkuNotAvailableException;
import com.afrisanjaya.shipment.logistics.domain.entity.Shipment;
import com.afrisanjaya.shipment.logistics.domain.entity.Sku;
import com.afrisanjaya.shipment.logistics.domain.entity.Warehouse;
import com.afrisanjaya.shipment.logistics.domain.enums.ShipmentStatus;
import com.afrisanjaya.shipment.logistics.domain.repository.ShipmentRepository;
import com.afrisanjaya.shipment.logistics.domain.repository.SkuRepository;
import com.afrisanjaya.shipment.logistics.domain.repository.WarehouseRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ShipmentService {

    private static final String EVENT_CREATED = "ShipmentCreated_v1";
    private static final String EVENT_DISPATCHED = "ShipmentDispatched_v1";
    private static final String EVENT_CANCELLED = "ShipmentCancelled_v1";
    private static final String EVENT_DELIVERED = "ShipmentDelivered_v1";

    private final ShipmentRepository shipmentRepository;
    private final SkuRepository skuRepository;
    private final WarehouseRepository warehouseRepository;
    private final OutboxService outboxService;

    @Transactional
    public ShipmentResponse createShipment(CreateShipmentRequest request, UUID idempotencyKey) {
        log.info("Creating shipment: idempotencyKey={}, tenantId={}, skuId={}",
                idempotencyKey, request.tenantId(), request.skuId());

        return shipmentRepository.findByIdempotencyKey(idempotencyKey)
                .map(this::toResponse)
                .orElseGet(() -> createNewShipment(request, idempotencyKey));
    }

    private ShipmentResponse createNewShipment(CreateShipmentRequest request, UUID idempotencyKey) {
        Warehouse origin = warehouseRepository.findById(request.originWarehouseId())
                .orElseThrow(() -> new WarehouseNotFoundException(request.originWarehouseId()));
        Warehouse dest = warehouseRepository.findById(request.destWarehouseId())
                .orElseThrow(() -> new WarehouseNotFoundException(request.destWarehouseId()));
        Sku sku = skuRepository.findById(request.skuId())
                .orElseThrow(() -> new ShipmentNotFoundException(request.skuId()));

        if (sku.getQuantity() < request.quantity()) {
            throw new SkuNotAvailableException(request.skuId(), request.quantity(), sku.getQuantity());
        }

        BigDecimal totalWeight = sku.getWeightKg() != null
                ? sku.getWeightKg().multiply(BigDecimal.valueOf(request.quantity()))
                : null;

        OffsetDateTime estimatedDelivery = OffsetDateTime.now(ZoneOffset.UTC);
        estimatedDelivery = switch (request.priority()) {
            case "SAME_DAY" -> estimatedDelivery.plusHours(8);
            case "EXPRESS"   -> estimatedDelivery.plusHours(24);
            default          -> estimatedDelivery.plusHours(72);
        };

        Shipment shipment = Shipment.builder()
                .idempotencyKey(idempotencyKey)
                .tenantId(request.tenantId())
                .userId(request.userId())
                .sku(sku)
                .originWarehouse(origin)
                .destWarehouse(dest)
                .quantity(request.quantity())
                .totalWeightKg(totalWeight)
                .priority(request.priority())
                .estimatedDeliveryAt(estimatedDelivery)
                .build();

        shipmentRepository.save(shipment);
        outboxService.publishEvent(shipment, EVENT_CREATED);

        log.info("Shipment created: id={} tenantId={} priority={}", shipment.getId(),
                request.tenantId(), request.priority());
        return toResponse(shipment);
    }

    @Transactional(readOnly = true)
    public ShipmentResponse getShipment(UUID id) {
        Shipment shipment = shipmentRepository.findById(id)
                .orElseThrow(() -> new ShipmentNotFoundException(id));
        return toResponse(shipment);
    }

    @Transactional
    public ShipmentResponse updateStatus(UUID shipmentId, ShipmentStatus newStatus) {
        log.info("Updating shipment status: id={} newStatus={}", shipmentId, newStatus);

        Shipment shipment = shipmentRepository.findById(shipmentId)
                .orElseThrow(() -> new ShipmentNotFoundException(shipmentId));

        if (!shipment.canTransitionTo(newStatus)) {
            throw new IllegalStateException(
                    "Cannot transition from " + shipment.getStatus() + " to " + newStatus);
        }

        shipment.setStatus(newStatus);

        switch (newStatus) {
            case DISPATCHED -> {
                shipment.setDispatchedAt(OffsetDateTime.now(ZoneOffset.UTC));
                outboxService.publishEvent(shipment, EVENT_DISPATCHED);
            }
            case DELIVERED -> {
                shipment.setDeliveredAt(OffsetDateTime.now(ZoneOffset.UTC));
                outboxService.publishEvent(shipment, EVENT_DELIVERED);
            }
            case CANCELLED -> {
                shipment.setCancelledAt(OffsetDateTime.now(ZoneOffset.UTC));
                outboxService.publishEvent(shipment, EVENT_CANCELLED);
            }
        }

        shipmentRepository.save(shipment);
        log.info("Shipment status updated: id={} newStatus={}", shipmentId, newStatus);
        return toResponse(shipment);
    }

    private ShipmentResponse toResponse(Shipment s) {
        return new ShipmentResponse(
                s.getId(), s.getIdempotencyKey(), s.getTenantId(), s.getUserId(),
                s.getSku().getId(), s.getOriginWarehouse().getId(), s.getDestWarehouse().getId(),
                s.getStatus(), s.getQuantity(), s.getTotalWeightKg(), s.getPriority(),
                s.getEstimatedDeliveryAt(), s.getCreatedAt()
        );
    }
}
