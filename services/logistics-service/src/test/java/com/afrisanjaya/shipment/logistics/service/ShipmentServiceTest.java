package com.afrisanjaya.shipment.logistics.service;

import com.afrisanjaya.shipment.logistics.api.dto.CreateShipmentRequest;
import com.afrisanjaya.shipment.logistics.api.exception.ShipmentNotFoundException;
import com.afrisanjaya.shipment.logistics.api.exception.SkuNotAvailableException;
import com.afrisanjaya.shipment.logistics.api.exception.WarehouseNotFoundException;
import com.afrisanjaya.shipment.logistics.domain.entity.Shipment;
import com.afrisanjaya.shipment.logistics.domain.entity.Sku;
import com.afrisanjaya.shipment.logistics.domain.entity.Warehouse;
import com.afrisanjaya.shipment.logistics.domain.enums.ShipmentStatus;
import com.afrisanjaya.shipment.logistics.domain.repository.ShipmentRepository;
import com.afrisanjaya.shipment.logistics.domain.repository.SkuRepository;
import com.afrisanjaya.shipment.logistics.domain.repository.WarehouseRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ShipmentServiceTest {

    private static final UUID TENANT_ID = UUID.fromString("a1b2c3d4-e5f6-7890-abcd-ef1234567801");
    private static final UUID USER_ID = UUID.fromString("a1b2c3d4-e5f6-7890-abcd-ef1234567802");

    @Mock
    private ShipmentRepository shipmentRepository;

    @Mock
    private SkuRepository skuRepository;

    @Mock
    private WarehouseRepository warehouseRepository;

    @Mock
    private OutboxService outboxService;

    @InjectMocks
    private ShipmentService shipmentService;

    private UUID idempotencyKey;
    private Warehouse originWarehouse;
    private Warehouse destWarehouse;
    private Sku sku;
    private CreateShipmentRequest standardRequest;

    @BeforeEach
    void setUp() {
        idempotencyKey = UUID.randomUUID();
        originWarehouse = warehouse(UUID.randomUUID(), "Origin");
        destWarehouse = warehouse(UUID.randomUUID(), "Destination");
        sku = sku(UUID.randomUUID(), 10, BigDecimal.valueOf(2.5));
        standardRequest = request("STANDARD", 2);
    }

    @Test
    void createShipment_whenDuplicateIdempotencyKey_thenReturnsExistingShipment() {
        Shipment existing = shipment(UUID.randomUUID(), ShipmentStatus.CREATED);
        given(shipmentRepository.findByIdempotencyKey(idempotencyKey)).willReturn(Optional.of(existing));

        var response = shipmentService.createShipment(standardRequest, idempotencyKey);

        assertThat(response.id()).isEqualTo(existing.getId());
        verify(shipmentRepository, never()).save(any());
        verify(outboxService, never()).publishEvent(any(), any());
    }

    @Test
    void createShipment_whenSkuNotFound_thenThrowsShipmentNotFoundException() {
        given(shipmentRepository.findByIdempotencyKey(idempotencyKey)).willReturn(Optional.empty());
        given(warehouseRepository.findById(originWarehouse.getId())).willReturn(Optional.of(originWarehouse));
        given(warehouseRepository.findById(destWarehouse.getId())).willReturn(Optional.of(destWarehouse));
        given(skuRepository.findById(sku.getId())).willReturn(Optional.empty());

        assertThatThrownBy(() -> shipmentService.createShipment(standardRequest, idempotencyKey))
                .isInstanceOf(ShipmentNotFoundException.class);
    }

    @Test
    void createShipment_whenWarehouseNotFound_thenThrowsWarehouseNotFoundException() {
        given(shipmentRepository.findByIdempotencyKey(idempotencyKey)).willReturn(Optional.empty());
        given(warehouseRepository.findById(originWarehouse.getId())).willReturn(Optional.empty());

        assertThatThrownBy(() -> shipmentService.createShipment(standardRequest, idempotencyKey))
                .isInstanceOf(WarehouseNotFoundException.class);
    }

    @Test
    void createShipment_whenSkuQuantityInsufficient_thenThrowsSkuNotAvailableException() {
        CreateShipmentRequest request = request("STANDARD", 11);
        givenCreateDependencies(request, sku);

        assertThatThrownBy(() -> shipmentService.createShipment(request, idempotencyKey))
                .isInstanceOf(SkuNotAvailableException.class);
    }

    @Test
    void createShipment_whenExpressPriority_thenEstimatedDeliveryIs24hFromNow() {
        assertEstimatedDeliveryHours(request("EXPRESS", 2), 24);
    }

    @Test
    void createShipment_whenStandardPriority_thenEstimatedDeliveryIs72hFromNow() {
        assertEstimatedDeliveryHours(request("STANDARD", 2), 72);
    }

    @Test
    void createShipment_whenSameDayPriority_thenEstimatedDeliveryIs8hFromNow() {
        assertEstimatedDeliveryHours(request("SAME_DAY", 2), 8);
    }

    @Test
    void createShipment_whenValid_thenSavesShipmentAndPublishesOutboxEvent() {
        givenCreateDependencies(standardRequest, sku);

        shipmentService.createShipment(standardRequest, idempotencyKey);

        ArgumentCaptor<Shipment> shipmentCaptor = ArgumentCaptor.forClass(Shipment.class);
        verify(shipmentRepository).save(shipmentCaptor.capture());
        verify(outboxService).publishEvent(shipmentCaptor.getValue(), "ShipmentCreated_v1");
        assertThat(shipmentCaptor.getValue().getTotalWeightKg()).isEqualByComparingTo("5.0");
    }

    @Test
    void updateStatus_whenInvalidTransition_thenThrowsIllegalStateException() {
        Shipment shipment = shipment(UUID.randomUUID(), ShipmentStatus.CREATED);
        given(shipmentRepository.findById(shipment.getId())).willReturn(Optional.of(shipment));

        assertThatThrownBy(() -> shipmentService.updateStatus(shipment.getId(), ShipmentStatus.DISPATCHED))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void updateStatus_whenDispatched_thenSetsDispatchedAtTimestamp() {
        Shipment shipment = shipment(UUID.randomUUID(), ShipmentStatus.PACKED);
        given(shipmentRepository.findById(shipment.getId())).willReturn(Optional.of(shipment));

        shipmentService.updateStatus(shipment.getId(), ShipmentStatus.DISPATCHED);

        assertThat(shipment.getDispatchedAt()).isNotNull();
        verify(outboxService).publishEvent(shipment, "ShipmentDispatched_v1");
    }

    @Test
    void updateStatus_whenDelivered_thenSetsDeliveredAtTimestamp() {
        Shipment shipment = shipment(UUID.randomUUID(), ShipmentStatus.IN_TRANSIT);
        given(shipmentRepository.findById(shipment.getId())).willReturn(Optional.of(shipment));

        shipmentService.updateStatus(shipment.getId(), ShipmentStatus.DELIVERED);

        assertThat(shipment.getDeliveredAt()).isNotNull();
        verify(outboxService).publishEvent(shipment, "ShipmentDelivered_v1");
    }

    @Test
    void updateStatus_whenCancelled_thenSetsCancelledAtTimestamp() {
        Shipment shipment = shipment(UUID.randomUUID(), ShipmentStatus.CREATED);
        given(shipmentRepository.findById(shipment.getId())).willReturn(Optional.of(shipment));

        shipmentService.updateStatus(shipment.getId(), ShipmentStatus.CANCELLED);

        assertThat(shipment.getCancelledAt()).isNotNull();
        verify(outboxService).publishEvent(shipment, "ShipmentCancelled_v1");
    }

    @Test
    void getShipment_whenNotFound_thenThrowsShipmentNotFoundException() {
        UUID shipmentId = UUID.randomUUID();
        given(shipmentRepository.findById(shipmentId)).willReturn(Optional.empty());

        assertThatThrownBy(() -> shipmentService.getShipment(shipmentId))
                .isInstanceOf(ShipmentNotFoundException.class);
    }

    private void assertEstimatedDeliveryHours(CreateShipmentRequest request, long expectedHours) {
        givenCreateDependencies(request, sku);
        OffsetDateTime before = OffsetDateTime.now(ZoneOffset.UTC).plusHours(expectedHours);

        shipmentService.createShipment(request, idempotencyKey);

        ArgumentCaptor<Shipment> shipmentCaptor = ArgumentCaptor.forClass(Shipment.class);
        verify(shipmentRepository).save(shipmentCaptor.capture());
        OffsetDateTime after = OffsetDateTime.now(ZoneOffset.UTC).plusHours(expectedHours);
        assertThat(shipmentCaptor.getValue().getEstimatedDeliveryAt())
                .isBetween(before.minus(Duration.ofSeconds(1)), after.plus(Duration.ofSeconds(1)));
    }

    private void givenCreateDependencies(CreateShipmentRequest request, Sku availableSku) {
        given(shipmentRepository.findByIdempotencyKey(idempotencyKey)).willReturn(Optional.empty());
        given(warehouseRepository.findById(request.originWarehouseId())).willReturn(Optional.of(originWarehouse));
        given(warehouseRepository.findById(request.destWarehouseId())).willReturn(Optional.of(destWarehouse));
        given(skuRepository.findById(request.skuId())).willReturn(Optional.of(availableSku));
    }

    private CreateShipmentRequest request(String priority, int quantity) {
        return new CreateShipmentRequest(
                TENANT_ID,
                USER_ID,
                sku.getId(),
                originWarehouse.getId(),
                destWarehouse.getId(),
                quantity,
                priority
        );
    }

    private Shipment shipment(UUID id, ShipmentStatus status) {
        return Shipment.builder()
                .id(id)
                .idempotencyKey(idempotencyKey)
                .tenantId(TENANT_ID)
                .userId(USER_ID)
                .sku(sku)
                .originWarehouse(originWarehouse)
                .destWarehouse(destWarehouse)
                .status(status)
                .quantity(2)
                .totalWeightKg(BigDecimal.valueOf(5.0))
                .priority("STANDARD")
                .estimatedDeliveryAt(OffsetDateTime.now(ZoneOffset.UTC).plusHours(72))
                .createdAt(OffsetDateTime.now(ZoneOffset.UTC))
                .build();
    }

    private Sku sku(UUID id, int quantity, BigDecimal weightKg) {
        return Sku.builder()
                .id(id)
                .tenantId(TENANT_ID)
                .skuCode("SKU-001")
                .name("Cold Chain Item")
                .category("PHARMA")
                .unitPrice(BigDecimal.TEN)
                .quantity(quantity)
                .weightKg(weightKg)
                .warehouse(originWarehouse)
                .build();
    }

    private Warehouse warehouse(UUID id, String name) {
        return Warehouse.builder()
                .id(id)
                .tenantId(TENANT_ID)
                .name(name)
                .location("Jakarta")
                .warehouseType("COLD_STORAGE")
                .capacity(100)
                .address("Jl. Test")
                .build();
    }
}
