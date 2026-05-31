package com.afrisanjaya.shipment.dataplatform.service;

import com.afrisanjaya.shipment.dataplatform.api.dto.CreateTenantRequest;
import com.afrisanjaya.shipment.dataplatform.api.dto.TenantResponse;
import com.afrisanjaya.shipment.dataplatform.api.exception.TenantNotFoundException;
import com.afrisanjaya.shipment.dataplatform.domain.entity.Tenant;
import com.afrisanjaya.shipment.dataplatform.domain.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class TenantService {

    private final TenantRepository tenantRepository;
    private static final SecureRandom RANDOM = new SecureRandom();

    @Transactional
    public TenantResponse createTenant(CreateTenantRequest request) {
        String apiKey = generateApiKey();

        while (tenantRepository.existsByApiKey(apiKey)) {
            apiKey = generateApiKey();
        }

        Tenant tenant = Tenant.builder()
                .name(request.name())
                .apiKey(apiKey)
                .plan(request.plan() != null ? request.plan() : "FREE")
                .build();

        tenantRepository.save(tenant);
        log.info("Tenant created: id={} name={} plan={}", tenant.getId(), tenant.getName(), tenant.getPlan());

        return toResponse(tenant);
    }

    @Transactional(readOnly = true)
    public TenantResponse getTenant(UUID id) {
        Tenant tenant = tenantRepository.findById(id)
                .orElseThrow(() -> new TenantNotFoundException(id));
        return toResponse(tenant);
    }

    @Transactional(readOnly = true)
    public TenantResponse getTenantByApiKey(String apiKey) {
        Tenant tenant = tenantRepository.findByApiKey(apiKey)
                .orElseThrow(() -> new TenantNotFoundException("API key not found"));
        return toResponse(tenant);
    }

    private String generateApiKey() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return "tp_" + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private TenantResponse toResponse(Tenant t) {
        return new TenantResponse(t.getId(), t.getName(), t.getApiKey(), t.getPlan(),
                t.isActive(), t.getCreatedAt());
    }
}
