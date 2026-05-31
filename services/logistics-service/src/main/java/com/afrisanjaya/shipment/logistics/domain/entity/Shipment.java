package com.afrisanjaya.shipment.logistics.domain.entity;

import com.afrisanjaya.shipment.logistics.domain.enums.ShipmentStatus;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "shipments")
@Getter @Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Shipment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "idempotency_key", nullable = false, unique = true, updatable = false)
    private UUID idempotencyKey;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sku_id", nullable = false)
    private Sku sku;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "origin_warehouse_id", nullable = false)
    private Warehouse originWarehouse;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "dest_warehouse_id", nullable = false)
    private Warehouse destWarehouse;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 50)
    private ShipmentStatus status;

    @Column(name = "quantity", nullable = false)
    private int quantity;

    @Column(name = "total_weight_kg", precision = 8, scale = 2)
    private BigDecimal totalWeightKg;

    @Column(name = "priority", nullable = false, length = 20)
    private String priority;

    @Column(name = "estimated_delivery_at")
    private OffsetDateTime estimatedDeliveryAt;

    @Column(name = "dispatched_at")
    private OffsetDateTime dispatchedAt;

    @Column(name = "delivered_at")
    private OffsetDateTime deliveredAt;

    @Column(name = "cancelled_at")
    private OffsetDateTime cancelledAt;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    protected void prePersist() {
        createdAt = OffsetDateTime.now(java.time.ZoneOffset.UTC);
        updatedAt = OffsetDateTime.now(java.time.ZoneOffset.UTC);
        status = ShipmentStatus.CREATED;
    }

    @PreUpdate
    protected void preUpdate() {
        updatedAt = OffsetDateTime.now(java.time.ZoneOffset.UTC);
    }

    public boolean canTransitionTo(ShipmentStatus target) {
        return switch (this.status) {
            case CREATED    -> target == ShipmentStatus.PICKED || target == ShipmentStatus.CANCELLED;
            case PICKED     -> target == ShipmentStatus.PACKED || target == ShipmentStatus.CANCELLED;
            case PACKED     -> target == ShipmentStatus.DISPATCHED;
            case DISPATCHED -> target == ShipmentStatus.IN_TRANSIT || target == ShipmentStatus.CANCELLED;
            case IN_TRANSIT -> target == ShipmentStatus.DELIVERED;
            case DELIVERED, CANCELLED -> false;
        };
    }
}
