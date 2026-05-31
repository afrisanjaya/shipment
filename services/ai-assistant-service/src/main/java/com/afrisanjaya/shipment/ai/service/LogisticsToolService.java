package com.afrisanjaya.shipment.ai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class LogisticsToolService {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${logistics.service.url:http://localhost:8081}")
    private String logisticsServiceUrl;

    public String getShipmentStatus(String shipmentId) {
        String url = logisticsServiceUrl + "/api/v1/shipments/" + shipmentId;
        log.info("[TOOL] GET {}", url);
        try {
            ResponseEntity<Map> resp = restTemplate.getForEntity(url, Map.class);
            Map body = resp.getBody();
            return objectMapper.writeValueAsString(Map.of(
                    "shipmentId", shipmentId,
                    "status", body != null ? body.getOrDefault("status", "UNKNOWN") : "UNKNOWN",
                    "details", body != null ? body : Map.of()));
        } catch (Exception e) {
            log.error("[TOOL] getShipmentStatus failed: {}", e.getMessage());
            return "{\"error\": \"Failed to retrieve shipment status\"}";
        }
    }

    public String checkInventory(String skuId, String warehouseId) {
        String url = logisticsServiceUrl + "/api/v1/inventory/check";
        if (!skuId.isBlank()) url += "?skuId=" + skuId;
        if (!warehouseId.isBlank()) url += (url.contains("?") ? "&" : "?") + "warehouseId=" + warehouseId;
        log.info("[TOOL] GET {}", url);
        try {
            ResponseEntity<Map> resp = restTemplate.getForEntity(url, Map.class);
            return objectMapper.writeValueAsString(
                    resp.getBody() != null ? resp.getBody() : Map.of("available", 0));
        } catch (Exception e) {
            log.error("[TOOL] checkInventory failed: {}", e.getMessage());
            return "{\"error\": \"Failed to check inventory\"}";
        }
    }

    public String getShipmentDetails(String shipmentId) {
        String url = logisticsServiceUrl + "/api/v1/shipments/" + shipmentId + "/details";
        log.info("[TOOL] GET {}", url);
        try {
            ResponseEntity<Map> resp = restTemplate.getForEntity(url, Map.class);
            return objectMapper.writeValueAsString(resp.getBody() != null ? resp.getBody() : Map.of());
        } catch (Exception e) {
            log.error("[TOOL] getShipmentDetails failed: {}", e.getMessage());
            return "{\"error\": \"Failed to retrieve shipment details\"}";
        }
    }

    public String checkSlaRisk(String shipmentId) {
        String url = logisticsServiceUrl + "/api/v1/shipments/" + shipmentId + "/sla-risk";
        log.info("[TOOL] GET {}", url);
        try {
            ResponseEntity<Map> resp = restTemplate.getForEntity(url, Map.class);
            return objectMapper.writeValueAsString(
                    resp.getBody() != null ? resp.getBody() : Map.of("atRisk", false));
        } catch (Exception e) {
            log.error("[TOOL] checkSlaRisk failed: {}", e.getMessage());
            return "{\"error\": \"Failed to check SLA risk\"}";
        }
    }

    public String getSensorHealth(String shipmentId, String sensorType) {
        String url = logisticsServiceUrl + "/api/v1/shipments/" + shipmentId + "/sensors";
        if (!sensorType.isBlank()) url += "?type=" + sensorType;
        log.info("[TOOL] GET {}", url);
        try {
            ResponseEntity<Map> resp = restTemplate.getForEntity(url, Map.class);
            return objectMapper.writeValueAsString(
                    resp.getBody() != null ? resp.getBody() : Map.of("readings", java.util.List.of()));
        } catch (Exception e) {
            log.error("[TOOL] getSensorHealth failed: {}", e.getMessage());
            return "{\"error\": \"Failed to retrieve sensor readings\"}";
        }
    }

    public String createShipment(String tenantId, String originWarehouseId,
                                  String destWarehouseId, String skuId, int quantity) {
        log.info("[TOOL] Creating shipment for tenant={}, origin={}, dest={}, sku={}, qty={}",
                tenantId, originWarehouseId, destWarehouseId, skuId, quantity);
        try {
            var body = Map.of(
                    "tenantId", tenantId,
                    "originWarehouseId", originWarehouseId,
                    "destWarehouseId", destWarehouseId,
                    "skuId", skuId,
                    "quantity", quantity
            );
            var resp = restTemplate.postForEntity(
                    logisticsServiceUrl + "/api/v1/shipments", body, Map.class);
            return resp.getBody() != null ? resp.getBody().toString() : "Shipment created.";
        } catch (Exception e) {
            log.error("[TOOL] Failed to create shipment: {}", e.getMessage());
            return "Failed to create shipment: " + e.getMessage();
        }
    }
}
