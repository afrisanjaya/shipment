package com.afrisanjaya.shipment.dataplatform.service;

import com.afrisanjaya.shipment.dataplatform.api.dto.CreateTenantRequest;
import com.afrisanjaya.shipment.dataplatform.api.dto.TenantResponse;
import com.afrisanjaya.shipment.dataplatform.api.exception.TenantNotFoundException;
import com.afrisanjaya.shipment.dataplatform.domain.entity.Tenant;
import com.afrisanjaya.shipment.dataplatform.domain.repository.TenantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TenantServiceTest {

    @Mock
    private TenantRepository tenantRepository;

    @InjectMocks
    private TenantService tenantService;


    @Test
    void createTenant_whenValid_thenGeneratesApiKeyWithTpPrefix() {
        CreateTenantRequest request = new CreateTenantRequest("Acme Corp", "PRO");
        given(tenantRepository.existsByApiKey(any())).willReturn(false);

        ArgumentCaptor<Tenant> captor = ArgumentCaptor.forClass(Tenant.class);
        given(tenantRepository.save(captor.capture())).willAnswer(inv -> {
            Tenant t = inv.getArgument(0);
            t.setId(UUID.randomUUID());
            return t;
        });

        TenantResponse response = tenantService.createTenant(request);

        assertThat(response.apiKey()).startsWith("tp_");
        assertThat(response.apiKey()).hasSizeGreaterThan(10);
        assertThat(response.name()).isEqualTo("Acme Corp");
        assertThat(response.plan()).isEqualTo("PRO");
    }


    @Test
    void createTenant_whenApiKeyCollision_thenRegeneratesUntilUnique() {
        CreateTenantRequest request = new CreateTenantRequest("Collision Corp", "PRO");
        given(tenantRepository.existsByApiKey(any()))
                .willReturn(true)
                .willReturn(false);

        ArgumentCaptor<Tenant> captor = ArgumentCaptor.forClass(Tenant.class);
        given(tenantRepository.save(captor.capture())).willAnswer(inv -> {
            Tenant t = inv.getArgument(0);
            t.setId(UUID.randomUUID());
            return t;
        });

        TenantResponse response = tenantService.createTenant(request);

        verify(tenantRepository, times(2)).existsByApiKey(any());
        assertThat(response.apiKey()).startsWith("tp_");
        assertThat(captor.getValue().getApiKey()).startsWith("tp_");
    }


    @Test
    void createTenant_whenPlanNull_thenDefaultsToFree() {
        CreateTenantRequest request = new CreateTenantRequest("Startup Ltd", null);
        given(tenantRepository.existsByApiKey(any())).willReturn(false);

        ArgumentCaptor<Tenant> captor = ArgumentCaptor.forClass(Tenant.class);
        given(tenantRepository.save(captor.capture())).willAnswer(inv -> {
            Tenant t = inv.getArgument(0);
            t.setId(UUID.randomUUID());
            return t;
        });

        TenantResponse response = tenantService.createTenant(request);

        assertThat(response.plan()).isEqualTo("FREE");
    }


    @Test
    void getTenant_whenNotFound_thenThrowsTenantNotFoundException() {
        UUID unknownId = UUID.randomUUID();
        given(tenantRepository.findById(unknownId)).willReturn(Optional.empty());

        assertThatThrownBy(() -> tenantService.getTenant(unknownId))
                .isInstanceOf(TenantNotFoundException.class)
                .hasMessageContaining(unknownId.toString());
    }


    @Test
    void getTenantByApiKey_whenInvalidKey_thenThrowsTenantNotFoundException() {
        String bogusKey = "tp_not-a-real-key";
        given(tenantRepository.findByApiKey(bogusKey)).willReturn(Optional.empty());

        assertThatThrownBy(() -> tenantService.getTenantByApiKey(bogusKey))
                .isInstanceOf(TenantNotFoundException.class)
                .hasMessageContaining("API key not found");
    }


    @Test
    void getTenant_whenFound_thenReturnsCorrectResponse() {
        UUID tenantId = UUID.randomUUID();
        Tenant tenant = Tenant.builder()
                .name("Found Corp")
                .apiKey("tp_found-key")
                .plan("ENTERPRISE")
                .isActive(true)
                .build();
        tenant.setId(tenantId);

        given(tenantRepository.findById(tenantId)).willReturn(Optional.of(tenant));

        TenantResponse response = tenantService.getTenant(tenantId);

        assertThat(response.id()).isEqualTo(tenantId);
        assertThat(response.name()).isEqualTo("Found Corp");
        assertThat(response.plan()).isEqualTo("ENTERPRISE");
        assertThat(response.isActive()).isTrue();
    }
}
