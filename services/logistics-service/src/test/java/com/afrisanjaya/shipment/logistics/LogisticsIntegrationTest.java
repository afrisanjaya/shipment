package com.afrisanjaya.shipment.logistics;

import com.afrisanjaya.shipment.logistics.api.dto.CreateShipmentRequest;
import com.afrisanjaya.shipment.logistics.domain.entity.OutboxEvent;
import com.afrisanjaya.shipment.logistics.domain.entity.Shipment;
import com.afrisanjaya.shipment.logistics.domain.enums.ShipmentStatus;
import com.afrisanjaya.shipment.logistics.domain.repository.OutboxEventRepository;
import com.afrisanjaya.shipment.logistics.domain.repository.ShipmentRepository;
import com.afrisanjaya.shipment.logistics.domain.repository.SkuRepository;
import com.afrisanjaya.shipment.logistics.domain.repository.WarehouseRepository;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestConstructor;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class LogisticsIntegrationTest {

    private static final UUID TENANT_ID = UUID.fromString("a1b2c3d4-e5f6-7890-abcd-ef1234567801");
    private static final UUID USER_ID = UUID.fromString("a1b2c3d4-e5f6-7890-abcd-ef1234567802");
    private static final UUID ORIGIN_WAREHOUSE_ID = UUID.fromString("f1e2d3c4-b5a6-7890-1234-567890abcdef");
    private static final UUID DEST_WAREHOUSE_ID = UUID.fromString("f2e3d4c5-b6a7-8901-2345-678901bcdef0");
    private static final UUID SKU_ID = UUID.fromString("b1c2d3e4-f5a6-7890-1234-567890abcdef");

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    private final com.afrisanjaya.shipment.logistics.service.ShipmentService shipmentService;
    private final ShipmentRepository shipmentRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final WarehouseRepository warehouseRepository;
    private final SkuRepository skuRepository;
    private final TransactionTemplate transactionTemplate;

    @MockBean
    private DynamoDbClient dynamoDbClient;

    LogisticsIntegrationTest(
            com.afrisanjaya.shipment.logistics.service.ShipmentService shipmentService,
            ShipmentRepository shipmentRepository,
            OutboxEventRepository outboxEventRepository,
            WarehouseRepository warehouseRepository,
            SkuRepository skuRepository,
            TransactionTemplate transactionTemplate) {
        this.shipmentService = shipmentService;
        this.shipmentRepository = shipmentRepository;
        this.outboxEventRepository = outboxEventRepository;
        this.warehouseRepository = warehouseRepository;
        this.skuRepository = skuRepository;
        this.transactionTemplate = transactionTemplate;
    }

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.kafka.bootstrap-servers", () -> "localhost:9092");
    }

    @Test
    void createShipment_fullRoundTrip_persistsAndRetrievesFromDb() {
        UUID idempotencyKey = UUID.randomUUID();

        var created = shipmentService.createShipment(createRequest(), idempotencyKey);
        var retrieved = shipmentService.getShipment(created.id());

        assertThat(retrieved.id()).isEqualTo(created.id());
        assertThat(retrieved.status()).isEqualTo(ShipmentStatus.CREATED);
        assertThat(retrieved.skuId()).isEqualTo(SKU_ID);
    }

    @Test
    void updateStatus_dispatchedThenDelivered_outboxHasTwoEvents() {
        var created = shipmentService.createShipment(createRequest(), UUID.randomUUID());

        shipmentService.updateStatus(created.id(), ShipmentStatus.PICKED);
        shipmentService.updateStatus(created.id(), ShipmentStatus.PACKED);
        shipmentService.updateStatus(created.id(), ShipmentStatus.DISPATCHED);
        shipmentService.updateStatus(created.id(), ShipmentStatus.IN_TRANSIT);
        shipmentService.updateStatus(created.id(), ShipmentStatus.DELIVERED);

        var eventTypes = outboxEventRepository.findAll().stream()
                .filter(e -> e.getAggregateId().equals(created.id()))
                .map(OutboxEvent::getEventType)
                .toList();
        assertThat(eventTypes)
                .contains("ShipmentDispatched_v1", "ShipmentDelivered_v1");
    }

    @Test
    void findByIdempotencyKey_afterCreate_returnsCorrectShipment() {
        UUID idempotencyKey = UUID.randomUUID();
        var created = shipmentService.createShipment(createRequest(), idempotencyKey);

        var found = shipmentRepository.findByIdempotencyKey(idempotencyKey);

        assertThat(found).isPresent();
        assertThat(found.orElseThrow().getId()).isEqualTo(created.id());
    }

    @Test
    void createShipment_whenExceptionMidTx_thenNeitherEntityNorOutboxPersists() {
        UUID idempotencyKey = UUID.randomUUID();
        long outboxCountBefore = outboxEventRepository.count();

        try {
            transactionTemplate.executeWithoutResult(status -> {
                Shipment shipment = Shipment.builder()
                        .idempotencyKey(idempotencyKey)
                        .tenantId(TENANT_ID)
                        .userId(USER_ID)
                        .sku(skuRepository.findById(SKU_ID).orElseThrow())
                        .originWarehouse(warehouseRepository.findById(ORIGIN_WAREHOUSE_ID).orElseThrow())
                        .destWarehouse(warehouseRepository.findById(DEST_WAREHOUSE_ID).orElseThrow())
                        .status(ShipmentStatus.CREATED)
                        .quantity(1)
                        .totalWeightKg(BigDecimal.valueOf(0.25))
                        .priority("STANDARD")
                        .estimatedDeliveryAt(OffsetDateTime.now(ZoneOffset.UTC).plusHours(72))
                        .build();
                shipmentRepository.save(shipment);
                outboxEventRepository.save(OutboxEvent.builder()
                        .aggregateType("Shipment")
                        .aggregateId(shipment.getId())
                        .eventType("ShipmentCreated_v1")
                        .payload(Map.of("shipmentId", shipment.getId().toString()))
                        .build());
                throw new IllegalStateException("simulated transaction failure");
            });
        } catch (IllegalStateException exception) {
            assertThat(exception.getMessage()).isEqualTo("simulated transaction failure");
        }

        assertThat(shipmentRepository.findByIdempotencyKey(idempotencyKey)).isEmpty();
        assertThat(outboxEventRepository.count()).isEqualTo(outboxCountBefore);
    }

    private CreateShipmentRequest createRequest() {
        return new CreateShipmentRequest(
                TENANT_ID,
                USER_ID,
                SKU_ID,
                ORIGIN_WAREHOUSE_ID,
                DEST_WAREHOUSE_ID,
                1,
                "STANDARD"
        );
    }
}
