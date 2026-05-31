package com.afrisanjaya.shipment.dataplatform.domain.repository;

import com.afrisanjaya.shipment.dataplatform.domain.entity.DocumentAuditResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface DocumentAuditResultRepository extends JpaRepository<DocumentAuditResult, UUID> {

    List<DocumentAuditResult> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);

    List<DocumentAuditResult> findByAuditStatusOrderByCreatedAtAsc(String auditStatus);
}
