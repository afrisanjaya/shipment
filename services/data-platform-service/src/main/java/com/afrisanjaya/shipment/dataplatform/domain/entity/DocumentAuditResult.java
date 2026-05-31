package com.afrisanjaya.shipment.dataplatform.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "document_audit_results")
@Getter @Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocumentAuditResult {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "upload_id", nullable = false)
    private UUID uploadId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "document_type", nullable = false, length = 100)
    private String documentType;

    @Column(name = "extracted_text", columnDefinition = "TEXT")
    private String extractedText;

    @Column(name = "audit_status", nullable = false, length = 50)
    private String auditStatus;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "compliance_checks", columnDefinition = "jsonb")
    private Map<String, Object> complianceChecks;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "discrepancy_details", columnDefinition = "jsonb")
    private Map<String, Object> discrepancyDetails;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "audited_at")
    private OffsetDateTime auditedAt;

    @PrePersist
    protected void prePersist() {
        createdAt = OffsetDateTime.now();
        if (auditStatus == null) auditStatus = "PENDING";
    }
}
