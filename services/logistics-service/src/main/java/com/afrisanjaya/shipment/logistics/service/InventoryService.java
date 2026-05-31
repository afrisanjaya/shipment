package com.afrisanjaya.shipment.logistics.service;

import com.afrisanjaya.shipment.logistics.api.dto.SkuResponse;
import com.afrisanjaya.shipment.logistics.api.exception.ShipmentNotFoundException;
import com.afrisanjaya.shipment.logistics.domain.entity.Sku;
import com.afrisanjaya.shipment.logistics.domain.repository.SkuRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class InventoryService {

    private final SkuRepository skuRepository;

    @Transactional(readOnly = true)
    public Map<String, Object> checkBySku(UUID skuId) {
        Sku sku = skuRepository.findById(skuId)
                .orElseThrow(() -> new ShipmentNotFoundException(skuId));
        log.debug("Inventory check by SKU: skuId={} available={}", skuId, sku.getQuantity());
        return Map.of(
                "skuId", sku.getId(),
                "skuCode", sku.getSkuCode(),
                "name", sku.getName(),
                "available", sku.getQuantity(),
                "warehouseId", sku.getWarehouse().getId()
        );
    }

    @Transactional(readOnly = true)
    public Map<String, Object> checkByWarehouse(UUID warehouseId) {
        List<Sku> skus = skuRepository.findByWarehouseIdAndIsActiveTrue(warehouseId);
        int total = skus.stream().mapToInt(Sku::getQuantity).sum();
        log.debug("Inventory check by warehouse: warehouseId={} totalItems={} skuCount={}",
                warehouseId, total, skus.size());
        return Map.of(
                "warehouseId", warehouseId,
                "totalItems", total,
                "skuCount", skus.size()
        );
    }
}
