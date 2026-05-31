package com.afrisanjaya.shipment.dataplatform.service;

import com.afrisanjaya.shipment.dataplatform.api.SseEmitterRegistry;
import com.afrisanjaya.shipment.dataplatform.api.dto.DataQueryResponse;
import com.afrisanjaya.shipment.dataplatform.api.dto.IngestDataRequest;
import com.afrisanjaya.shipment.dataplatform.api.exception.TenantNotFoundException;
import com.afrisanjaya.shipment.dataplatform.domain.entity.Tenant;
import com.afrisanjaya.shipment.dataplatform.domain.entity.TenantData;
import com.afrisanjaya.shipment.dataplatform.domain.repository.TenantDataRepository;
import com.afrisanjaya.shipment.dataplatform.domain.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class DataIngestionService {

    private final TenantRepository tenantRepository;
    private final TenantDataRepository tenantDataRepository;
    private final SseEmitterRegistry sseEmitterRegistry;
    private final WebhookDispatcherService webhookDispatcherService;

    @Transactional
    public DataQueryResponse ingest(UUID tenantId, IngestDataRequest request) {
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new TenantNotFoundException(tenantId));

        TenantData data = TenantData.builder()
                .tenant(tenant)
                .dataType(request.dataType() != null ? request.dataType() : "generic")
                .payload(request.payload())
                .build();

        tenantDataRepository.save(data);
        sseEmitterRegistry.broadcast(tenantId, toStreamPayload(tenantId, data));

        webhookDispatcherService.dispatch(tenantId, "data.ingested",
                toStreamPayload(tenantId, data));

        log.info("Data ingested: tenantId={} dataType={}", tenantId, data.getDataType());

        return new DataQueryResponse(Map.of("status", "ingested", "id", data.getId().toString()));
    }

    @Transactional(readOnly = true)
    public DataQueryResponse query(UUID tenantId, String dataType,
                                    OffsetDateTime from, OffsetDateTime to,
                                    int page, int size) {
        var results = tenantDataRepository.queryData(tenantId, dataType, from, to,
                PageRequest.of(page, size));

        return new DataQueryResponse(Map.of(
                "totalElements", results.getTotalElements(),
                "totalPages", results.getTotalPages(),
                "page", page,
                "size", size,
                "data", results.getContent().stream()
                        .map(d -> Map.of(
                                "id", d.getId(),
                                "dataType", d.getDataType(),
                                "payload", d.getPayload(),
                                "createdAt", d.getCreatedAt().toString()
                        )).toList()
        ));
    }

    private Map<String, Object> toStreamPayload(UUID tenantId, TenantData data) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("id", data.getId());
        payload.put("tenantId", tenantId);
        payload.put("dataType", data.getDataType());
        payload.put("payload", data.getPayload());
        payload.put("createdAt", data.getCreatedAt());
        return payload;
    }
}
