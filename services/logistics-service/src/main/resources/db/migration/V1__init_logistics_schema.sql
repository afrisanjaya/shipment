CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- ── Warehouses ──────────────────────────────────────────────────────
CREATE TABLE warehouses (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    name VARCHAR(255) NOT NULL,
    location VARCHAR(255) NOT NULL,
    warehouse_type VARCHAR(50) NOT NULL DEFAULT 'DRY',
    capacity INT NOT NULL DEFAULT 0,
    address TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_warehouses_location ON warehouses(location);
CREATE INDEX idx_warehouses_tenant ON warehouses(tenant_id);
CREATE INDEX idx_warehouses_type ON warehouses(warehouse_type);

-- ── SKUs (Stock Keeping Units) ─────────────────────────────────────
CREATE TABLE skus (
    id UUID PRIMARY KEY,
    warehouse_id UUID NOT NULL REFERENCES warehouses(id),
    tenant_id UUID NOT NULL,
    sku_code VARCHAR(50) NOT NULL,
    name VARCHAR(255) NOT NULL,
    category VARCHAR(100) NOT NULL,
    unit_price NUMERIC(12,2) NOT NULL DEFAULT 0,
    quantity INT NOT NULL DEFAULT 0,
    weight_kg NUMERIC(8,2),
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_skus_warehouse ON skus(warehouse_id);
CREATE INDEX idx_skus_tenant ON skus(tenant_id);
CREATE UNIQUE INDEX idx_skus_code ON skus(tenant_id, sku_code);

-- ── Shipments ───────────────────────────────────────────────────────
CREATE TABLE shipments (
    id UUID PRIMARY KEY,
    idempotency_key UUID NOT NULL UNIQUE,
    tenant_id UUID NOT NULL,
    user_id UUID NOT NULL,
    sku_id UUID NOT NULL REFERENCES skus(id),
    origin_warehouse_id UUID NOT NULL REFERENCES warehouses(id),
    dest_warehouse_id UUID NOT NULL REFERENCES warehouses(id),
    status VARCHAR(50) NOT NULL DEFAULT 'CREATED',
    quantity INT NOT NULL DEFAULT 1,
    total_weight_kg NUMERIC(8,2),
    priority VARCHAR(20) NOT NULL DEFAULT 'STANDARD',
    estimated_delivery_at TIMESTAMPTZ,
    dispatched_at TIMESTAMPTZ,
    delivered_at TIMESTAMPTZ,
    cancelled_at TIMESTAMPTZ,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_shipments_tenant ON shipments(tenant_id);
CREATE INDEX idx_shipments_user ON shipments(user_id);
CREATE INDEX idx_shipments_status ON shipments(status);

-- ── Shipment Tracking (GPS pings from Task 2) ───────────────────────
CREATE TABLE shipment_tracking (
    id UUID PRIMARY KEY,
    shipment_id UUID NOT NULL REFERENCES shipments(id),
    tenant_id UUID NOT NULL,
    timestamp TIMESTAMPTZ NOT NULL,
    latitude DOUBLE PRECISION NOT NULL,
    longitude DOUBLE PRECISION NOT NULL,
    speed_kph DOUBLE PRECISION,
    heading DOUBLE PRECISION,
    accuracy DOUBLE PRECISION
);

CREATE INDEX idx_tracking_shipment ON shipment_tracking(shipment_id, timestamp DESC);

-- ── Sensor Readings (cold chain monitoring) ────────────────────────
CREATE TABLE sensor_readings (
    id UUID PRIMARY KEY,
    shipment_id UUID NOT NULL REFERENCES shipments(id),
    tenant_id UUID NOT NULL,
    timestamp TIMESTAMPTZ NOT NULL,
    sensor_type VARCHAR(50) NOT NULL,
    value DOUBLE PRECISION NOT NULL,
    unit VARCHAR(20) NOT NULL,
    threshold_min DOUBLE PRECISION,
    threshold_max DOUBLE PRECISION,
    is_in_range BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE INDEX idx_sensors_shipment ON sensor_readings(shipment_id, timestamp DESC);

-- ── Outbox Events ───────────────────────────────────────────────────
CREATE TABLE outbox_events (
    id UUID PRIMARY KEY,
    aggregate_type VARCHAR(100) NOT NULL,
    aggregate_id UUID NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    payload JSONB NOT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    published_at TIMESTAMPTZ
);

CREATE INDEX idx_outbox_events_status ON outbox_events(status);
