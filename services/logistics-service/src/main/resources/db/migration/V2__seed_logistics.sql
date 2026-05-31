-- Seed data for logistics service demo
-- Tenant: demo-tenant (UUID: a1b2c3d4-e5f6-7890-abcd-ef1234567801)

INSERT INTO warehouses (id, tenant_id, name, location, warehouse_type, capacity, address) VALUES
('f1e2d3c4-b5a6-7890-1234-567890abcdef', 'a1b2c3d4-e5f6-7890-abcd-ef1234567801',
 'Jakarta Cargo Hub', 'Jakarta Utara', 'DRY', 50000,
 'Jl. Raya Pelabuhan No. 88, Tanjung Priok, Jakarta Utara'),
('f2e3d4c5-b6a7-8901-2345-678901bcdef0', 'a1b2c3d4-e5f6-7890-abcd-ef1234567801',
 'Bandung Cold Chain', 'Bandung', 'COLD_STORAGE', 12000,
 'Jl. Industri Raya No. 45, Dayeuhkolot, Bandung'),
('f3e4d5c6-b7a8-9012-3456-789012cdef01', 'a1b2c3d4-e5f6-7890-abcd-ef1234567801',
 'Surabaya Cross-Dock', 'Surabaya', 'CROSS_DOCK', 8000,
 'Jl. Perak Timur No. 120, Surabaya');

INSERT INTO skus (id, warehouse_id, tenant_id, sku_code, name, category, unit_price, quantity, weight_kg) VALUES
('b1c2d3e4-f5a6-7890-1234-567890abcdef', 'f1e2d3c4-b5a6-7890-1234-567890abcdef',
 'a1b2c3d4-e5f6-7890-abcd-ef1234567801', 'ELEC-001', 'Smartphone X10', 'Electronics', 3500000, 500, 0.25),
('b2c3d4e5-f6a7-8901-2345-678901bcdef0', 'f1e2d3c4-b5a6-7890-1234-567890abcdef',
 'a1b2c3d4-e5f6-7890-abcd-ef1234567801', 'ELEC-002', 'Tablet Pro 12"', 'Electronics', 5500000, 200, 0.5),
('b3c4d5e6-f7a8-9012-3456-789012cdef01', 'f2e3d4c5-b6a7-8901-2345-678901bcdef0',
 'a1b2c3d4-e5f6-7890-abcd-ef1234567801', 'PHARMA-001', 'Vaksin Cold Chain A', 'Pharmaceuticals', 150000, 10000, 0.05),
('b4c5d6e7-f8a9-0123-4567-890123def012', 'f2e3d4c5-b6a7-8901-2345-678901bcdef0',
 'a1b2c3d4-e5f6-7890-abcd-ef1234567801', 'FOOD-001', 'Frozen Wagyu A5', 'Food', 850000, 300, 1.0);
