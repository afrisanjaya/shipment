package com.afrisanjaya.shipment.logistics.service;

import com.afrisanjaya.shipment.logistics.api.dto.WarehouseResponse;
import com.afrisanjaya.shipment.logistics.api.exception.WarehouseNotFoundException;
import com.afrisanjaya.shipment.logistics.domain.entity.Warehouse;
import com.afrisanjaya.shipment.logistics.domain.repository.WarehouseRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class WarehouseQueryService {

    private final WarehouseRepository warehouseRepository;

    @Transactional(readOnly = true)
    public Page<WarehouseResponse> listWarehouses(String location, String type, int page, int size) {
        Page<Warehouse> warehouses = type.isBlank()
                ? warehouseRepository.findByLocationContainingIgnoreCase(location, PageRequest.of(page, size))
                : warehouseRepository.findByLocationContainingIgnoreCaseAndWarehouseType(
                        location, type, PageRequest.of(page, size));
        log.debug("listWarehouses: location='{}' type='{}' page={} totalResults={}",
                location, type, page, warehouses.getTotalElements());
        return warehouses.map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public WarehouseResponse getById(UUID warehouseId) {
        Warehouse w = warehouseRepository.findById(warehouseId)
                .orElseThrow(() -> new WarehouseNotFoundException(warehouseId));
        return toResponse(w);
    }

    private WarehouseResponse toResponse(Warehouse w) {
        return new WarehouseResponse(
                w.getId(), w.getTenantId(), w.getName(), w.getLocation(),
                w.getWarehouseType(), w.getCapacity(), w.getAddress());
    }
}
