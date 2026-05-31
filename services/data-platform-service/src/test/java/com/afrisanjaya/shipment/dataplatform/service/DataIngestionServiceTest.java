package com.afrisanjaya.shipment.dataplatform.service;

import com.afrisanjaya.shipment.dataplatform.api.SseEmitterRegistry;
import com.afrisanjaya.shipment.dataplatform.api.dto.DataQueryResponse;
import com.afrisanjaya.shipment.dataplatform.service.WebhookDispatcherService;
import com.afrisanjaya.shipment.dataplatform.api.dto.IngestDataRequest;
import com.afrisanjaya.shipment.dataplatform.api.exception.TenantNotFoundException;
import com.afrisanjaya.shipment.dataplatform.domain.entity.Tenant;
import com.afrisanjaya.shipment.dataplatform.domain.entity.TenantData;
import com.afrisanjaya.shipment.dataplatform.domain.repository.TenantDataRepository;
import com.afrisanjaya.shipment.dataplatform.domain.repository.TenantRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DataIngestionServiceTest {

    @Mock
    private TenantRepository tenantRepository;

    @Mock
    private TenantDataRepository tenantDataRepository;

    @Mock
    private SseEmitterRegistry sseEmitterRegistry;

    @Mock
    private WebhookDispatcherService webhookDispatcherService;

    @InjectMocks
    private DataIngestionService dataIngestionService;


    @Test
    void ingest_whenTenantNotFound_thenThrowsTenantNotFoundException() {
        UUID unknownId = UUID.randomUUID();
        IngestDataRequest request = new IngestDataRequest("sensor",
                Map.of("temperature", 22.5));
        given(tenantRepository.findById(unknownId)).willReturn(Optional.empty());

        assertThatThrownBy(() -> dataIngestionService.ingest(unknownId, request))
                .isInstanceOf(TenantNotFoundException.class)
                .hasMessageContaining(unknownId.toString());

        verify(tenantDataRepository, never()).save(any());
        verify(sseEmitterRegistry, never()).broadcast(any(), any());
    }


    @Test
    void ingest_whenDataTypeNull_thenDefaultsToGeneric() {
        UUID tenantId = UUID.randomUUID();
        Tenant tenant = createTenant(tenantId);
        IngestDataRequest request = new IngestDataRequest(null,
                Map.of("key", "value"));

        given(tenantRepository.findById(tenantId)).willReturn(Optional.of(tenant));

        ArgumentCaptor<TenantData> captor = ArgumentCaptor.forClass(TenantData.class);
        given(tenantDataRepository.save(captor.capture())).willAnswer(inv -> {
            TenantData d = inv.getArgument(0);
            d.setId(UUID.randomUUID());
            return d;
        });
        doNothing().when(sseEmitterRegistry).broadcast(any(), any());

        DataQueryResponse response = dataIngestionService.ingest(tenantId, request);

        assertThat(captor.getValue().getDataType()).isEqualTo("generic");
        assertThat(response.result()).containsEntry("status", "ingested");
    }


    @Test
    void ingest_whenTenantExists_thenSavesTenantDataAndReturnsIngested() {
        UUID tenantId = UUID.randomUUID();
        Tenant tenant = createTenant(tenantId);
        IngestDataRequest request = new IngestDataRequest("inventory",
                Map.of("sku", "ITEM-001", "qty", 150));

        given(tenantRepository.findById(tenantId)).willReturn(Optional.of(tenant));

        ArgumentCaptor<TenantData> captor = ArgumentCaptor.forClass(TenantData.class);
        given(tenantDataRepository.save(captor.capture())).willAnswer(inv -> {
            TenantData d = inv.getArgument(0);
            d.setId(UUID.randomUUID());
            return d;
        });
        doNothing().when(sseEmitterRegistry).broadcast(any(), any());

        DataQueryResponse response = dataIngestionService.ingest(tenantId, request);

        TenantData saved = captor.getValue();
        assertThat(saved.getDataType()).isEqualTo("inventory");
        assertThat(saved.getPayload()).containsEntry("sku", "ITEM-001");
        assertThat(saved.getTenant()).isEqualTo(tenant);

        assertThat(response.result()).containsEntry("status", "ingested");
        assertThat(response.result()).containsKey("id");

        verify(sseEmitterRegistry).broadcast(eq(tenantId), any());
    }


    @Test
    void query_whenNoResults_thenReturnsEmptyPage() {
        UUID tenantId = UUID.randomUUID();
        Page<TenantData> emptyPage = new PageImpl<>(List.of());
        given(tenantDataRepository.queryData(
                eq(tenantId), isNull(), isNull(), isNull(), any(PageRequest.class)))
                .willReturn(emptyPage);

        DataQueryResponse response = dataIngestionService.query(
                tenantId, null, null, null, 0, 20);

        assertThat(response.result()).containsEntry("totalElements", 0L);
        assertThat(response.result()).containsEntry("totalPages", 1);
        //noinspection unchecked
        assertThat((List<?>) response.result().get("data")).isEmpty();
    }


    @Test
    void query_whenDataTypeFilter_thenReturnsPaginatedResults() {
        UUID tenantId = UUID.randomUUID();
        Tenant tenant = createTenant(tenantId);

        TenantData d1 = TenantData.builder()
                .tenant(tenant).dataType("sensor")
                .payload(Map.of("temp", 20.0))
                .createdAt(java.time.OffsetDateTime.now())
                .build();
        d1.setId(UUID.randomUUID());

        Page<TenantData> page = new PageImpl<>(List.of(d1));
        given(tenantDataRepository.queryData(
                eq(tenantId), eq("sensor"), isNull(), isNull(), any(PageRequest.class)))
                .willReturn(page);

        DataQueryResponse response = dataIngestionService.query(
                tenantId, "sensor", null, null, 0, 20);

        assertThat(response.result()).containsEntry("totalElements", 1L);
        //noinspection unchecked
        List<Map<String, Object>> data = (List<Map<String, Object>>) response.result().get("data");
        assertThat(data).hasSize(1);
        assertThat(data.get(0)).containsEntry("dataType", "sensor");
    }


    @Test
    void ingest_whenLargePayload_thenPersistsComplete() {
        UUID tenantId = UUID.randomUUID();
        Tenant tenant = createTenant(tenantId);
        Map<String, Object> largePayload = Map.of(
                "shipmentId", UUID.randomUUID().toString(),
                "coordinates", List.of(
                        Map.of("lat", -6.2, "lon", 106.8, "alt", 12.0),
                        Map.of("lat", -6.21, "lon", 106.81, "alt", 15.0),
                        Map.of("lat", -6.22, "lon", 106.82, "alt", 18.0)
                ),
                "metadata", Map.of("driver", "Budi", "vehicle", "B1234XYZ")
        );
        IngestDataRequest request = new IngestDataRequest("gps-tracking", largePayload);

        given(tenantRepository.findById(tenantId)).willReturn(Optional.of(tenant));

        ArgumentCaptor<TenantData> captor = ArgumentCaptor.forClass(TenantData.class);
        given(tenantDataRepository.save(captor.capture())).willAnswer(inv -> {
            TenantData d = inv.getArgument(0);
            d.setId(UUID.randomUUID());
            return d;
        });
        doNothing().when(sseEmitterRegistry).broadcast(any(), any());

        dataIngestionService.ingest(tenantId, request);

        TenantData saved = captor.getValue();
        assertThat(saved.getPayload()).hasSize(3);
        assertThat(saved.getPayload()).containsKey("coordinates");
        //noinspection unchecked
        List<Map<String, Object>> coords = (List<Map<String, Object>>) saved.getPayload().get("coordinates");
        assertThat(coords).hasSize(3);
    }


    private static Tenant createTenant(UUID id) {
        Tenant t = Tenant.builder()
                .name("Test Tenant")
                .apiKey("tp_test-key")
                .plan("PRO")
                .isActive(true)
                .build();
        t.setId(id);
        return t;
    }
}
