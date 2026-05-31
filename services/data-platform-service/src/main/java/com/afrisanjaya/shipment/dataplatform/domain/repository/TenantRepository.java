package com.afrisanjaya.shipment.dataplatform.domain.repository;

import com.afrisanjaya.shipment.dataplatform.domain.entity.Tenant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface TenantRepository extends JpaRepository<Tenant, UUID> {

    Optional<Tenant> findByApiKey(String apiKey);

    boolean existsByApiKey(String apiKey);
}
