CREATE TABLE chunked_uploads (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    file_name VARCHAR(255) NOT NULL,
    content_type VARCHAR(255) NOT NULL,
    total_chunks INT NOT NULL,
    received_chunks INT NOT NULL DEFAULT 0,
    total_size_bytes BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'INITIATED',
    storage_path TEXT,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    completed_at TIMESTAMPTZ
);

CREATE INDEX idx_chunked_uploads_tenant_status ON chunked_uploads(tenant_id, status);

CREATE TABLE chunked_upload_chunks (
    id UUID PRIMARY KEY,
    upload_id UUID NOT NULL REFERENCES chunked_uploads(id) ON DELETE CASCADE,
    chunk_index INT NOT NULL,
    chunk_data BYTEA NOT NULL,
    chunk_size_bytes INT NOT NULL,
    checksum VARCHAR(128),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX uk_chunked_upload_chunks_upload_chunk_index
    ON chunked_upload_chunks(upload_id, chunk_index);

CREATE TABLE webhook_subscriptions (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    callback_url VARCHAR(2048) NOT NULL,
    event_types VARCHAR(500) NOT NULL,
    secret VARCHAR(128),
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_webhook_subs_tenant ON webhook_subscriptions(tenant_id);

CREATE TABLE webhook_delivery_logs (
    id UUID PRIMARY KEY,
    subscription_id UUID NOT NULL REFERENCES webhook_subscriptions(id) ON DELETE CASCADE,
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    event_type VARCHAR(100) NOT NULL,
    payload JSONB NOT NULL,
    delivery_status VARCHAR(50) NOT NULL,
    attempt_count INT NOT NULL,
    max_attempts INT NOT NULL,
    last_response_code INT,
    last_response_body VARCHAR(2000),
    last_error_message VARCHAR(2000),
    next_retry_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    delivered_at TIMESTAMPTZ
);

CREATE INDEX idx_webhook_logs_sub ON webhook_delivery_logs(subscription_id);

CREATE TABLE document_audit_results (
    id UUID PRIMARY KEY,
    upload_id UUID NOT NULL REFERENCES chunked_uploads(id) ON DELETE CASCADE,
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    document_type VARCHAR(100) NOT NULL,
    extracted_text TEXT,
    audit_status VARCHAR(50) NOT NULL,
    compliance_checks JSONB,
    discrepancy_details JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    audited_at TIMESTAMPTZ
);

CREATE INDEX idx_doc_audits_upload ON document_audit_results(upload_id);
CREATE INDEX idx_doc_audits_tenant ON document_audit_results(tenant_id);
