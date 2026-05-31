package com.afrisanjaya.shipment.dataplatform.domain.repository;

import com.afrisanjaya.shipment.dataplatform.domain.entity.WebhookDeliveryLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface WebhookDeliveryLogRepository extends JpaRepository<WebhookDeliveryLog, UUID> {

    List<WebhookDeliveryLog> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);

    List<WebhookDeliveryLog> findByDeliveryStatusAndNextRetryAtBefore(String status,
                                                                      java.time.OffsetDateTime now);
}
