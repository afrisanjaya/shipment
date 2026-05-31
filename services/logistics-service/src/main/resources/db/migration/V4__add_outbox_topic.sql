-- V4: Add topic column to outbox_events for Debezium Kafka routing
-- The topic field tells Debezium which Kafka topic each event should be routed to.
-- Without this, Debezium publishes all events to a generic catch-all topic.

ALTER TABLE outbox_events
    ADD COLUMN IF NOT EXISTS topic VARCHAR(200);

-- Backfill existing rows based on event_type so old records are consistent
UPDATE outbox_events
SET topic = CASE event_type
    WHEN 'ShipmentCreated_v1'    THEN 'shipment-created'
    WHEN 'ShipmentDispatched_v1' THEN 'shipment-dispatched'
    WHEN 'ShipmentCancelled_v1'  THEN 'shipment-cancelled'
    WHEN 'ShipmentDelivered_v1'  THEN 'shipment-delivered'
    ELSE 'shipment-events'
END
WHERE topic IS NULL;
