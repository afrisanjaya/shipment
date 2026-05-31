package com.afrisanjaya.shipment.dataplatform.domain.repository;

import com.afrisanjaya.shipment.dataplatform.domain.entity.WebhookSubscription;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface WebhookSubscriptionRepository extends JpaRepository<WebhookSubscription, UUID> {

    List<WebhookSubscription> findByTenantIdAndIsActiveTrue(UUID tenantId);

    List<WebhookSubscription> findByTenantId(UUID tenantId);
}
