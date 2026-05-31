package com.afrisanjaya.shipment.logistics.api;

import com.afrisanjaya.shipment.logistics.api.dto.*;
import com.afrisanjaya.shipment.logistics.domain.enums.ShipmentStatus;
import com.afrisanjaya.shipment.logistics.domain.repository.DynamoDbSensorRepository;
import com.afrisanjaya.shipment.logistics.domain.repository.DynamoDbTrackingRepository;
import com.afrisanjaya.shipment.logistics.service.InventoryService;
import com.afrisanjaya.shipment.logistics.service.ShipmentService;
import com.afrisanjaya.shipment.logistics.service.WarehouseQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Tag(name = "Logistics API", description = "Supply Chain & Logistics Management")
public class ShipmentController {

    private final ShipmentService shipmentService;
    private final WarehouseQueryService warehouseQueryService;
    private final InventoryService inventoryService;
    private final DynamoDbSensorRepository sensorRepository;
    private final DynamoDbTrackingRepository trackingRepository;


    @Operation(summary = "List warehouses")
    @GetMapping("/warehouses")
    public ResponseEntity<Page<WarehouseResponse>> listWarehouses(
            @RequestParam(required = false, defaultValue = "") String location,
            @RequestParam(required = false, defaultValue = "") String type,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        return ResponseEntity.ok(warehouseQueryService.listWarehouses(location, type, page, size));
    }

    @Operation(summary = "Get warehouse by ID")
    @GetMapping("/warehouses/{warehouseId}")
    public ResponseEntity<WarehouseResponse> getWarehouse(@PathVariable UUID warehouseId) {
        return ResponseEntity.ok(warehouseQueryService.getById(warehouseId));
    }


    @Operation(summary = "Check inventory for a SKU or warehouse")
    @GetMapping("/inventory/check")
    public ResponseEntity<Map<String, Object>> checkInventory(
            @RequestParam(required = false) UUID skuId,
            @RequestParam(required = false) UUID warehouseId) {

        if (skuId != null) {
            return ResponseEntity.ok(inventoryService.checkBySku(skuId));
        }
        if (warehouseId != null) {
            return ResponseEntity.ok(inventoryService.checkByWarehouse(warehouseId));
        }
        return ResponseEntity.ok(Map.of("error", "Provide skuId or warehouseId"));
    }


    @Operation(summary = "Create shipment (CREATED status)")
    @PostMapping("/shipments")
    public ResponseEntity<ShipmentResponse> createShipment(
            @RequestHeader("Idempotency-Key") UUID idempotencyKey,
            @Valid @RequestBody CreateShipmentRequest request) {

        ShipmentResponse response = shipmentService.createShipment(request, idempotencyKey);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Operation(summary = "Get shipment by ID")
    @GetMapping("/shipments/{shipmentId}")
    public ResponseEntity<ShipmentResponse> getShipment(@PathVariable UUID shipmentId) {
        return ResponseEntity.ok(shipmentService.getShipment(shipmentId));
    }

    @Operation(summary = "Update shipment status (lifecycle transition)")
    @PatchMapping("/shipments/{shipmentId}/status")
    public ResponseEntity<ShipmentResponse> updateShipmentStatus(
            @PathVariable UUID shipmentId,
            @RequestBody Map<String, String> body) {

        ShipmentStatus newStatus = ShipmentStatus.valueOf(body.get("status").toUpperCase());
        return ResponseEntity.ok(shipmentService.updateStatus(shipmentId, newStatus));
    }

    @Operation(summary = "Get shipment details (needed by AI action group)")
    @GetMapping("/shipments/{shipmentId}/details")
    public ResponseEntity<ShipmentResponse> getShipmentDetails(@PathVariable UUID shipmentId) {
        return ResponseEntity.ok(shipmentService.getShipment(shipmentId));
    }

    @Operation(summary = "Check SLA risk for a shipment")
    @GetMapping("/shipments/{shipmentId}/sla-risk")
    public ResponseEntity<Map<String, Object>> checkSlaRisk(@PathVariable UUID shipmentId) {
        var shipment = shipmentService.getShipment(shipmentId);
        boolean atRisk = shipment.status() != ShipmentStatus.DELIVERED
                && shipment.estimatedDeliveryAt() != null
                && shipment.estimatedDeliveryAt().minusHours(2)
                        .isBefore(java.time.OffsetDateTime.now(java.time.ZoneOffset.UTC));
        return ResponseEntity.ok(Map.of(
                "shipmentId", shipmentId,
                "status", shipment.status().name(),
                "estimatedDelivery", shipment.estimatedDeliveryAt() != null
                        ? shipment.estimatedDeliveryAt().toString() : "N/A",
                "atRisk", atRisk));
    }

    @Operation(summary = "Get sensor readings from DynamoDB")
    @GetMapping("/shipments/{shipmentId}/sensors")
    public ResponseEntity<Map<String, Object>> getSensors(
            @PathVariable UUID shipmentId,
            @RequestParam(required = false, defaultValue = "") String type) {

        var readings = sensorRepository.findByShipmentId(
                shipmentId.toString(), type.isBlank() ? null : type);
        return ResponseEntity.ok(Map.of("readings", readings));
    }

    @Operation(summary = "Get GPS tracking data from DynamoDB")
    @GetMapping("/shipments/{shipmentId}/tracking")
    public ResponseEntity<Map<String, Object>> getTracking(
            @PathVariable UUID shipmentId,
            @RequestParam(defaultValue = "100") int limit) {

        var pings = trackingRepository.findByShipmentId(shipmentId.toString(), limit);
        return ResponseEntity.ok(Map.of("tracking", pings));
    }
}
