#!/bin/sh
echo "Registering Debezium CDC connectors..."

until curl -s http://debezium:8083/connectors > /dev/null; do
  echo "Waiting for Debezium Connect..."
  sleep 5
done

curl -s -X POST http://debezium:8083/connectors \
  -H "Content-Type: application/json" \
  -d '{
    "name": "logistics-outbox-connector",
    "config": {
      "connector.class": "io.debezium.connector.postgresql.PostgresConnector",
      "tasks.max": "1",
      "database.hostname": "postgres",
      "database.port": "5432",
      "database.user": "'"${POSTGRES_USER}"'",
      "database.password": "'"${POSTGRES_PASSWORD}"'",
      "database.dbname": "logistics_db",
      "topic.prefix": "logistics",
      "table.include.list": "public.outbox_events",
      "plugin.name": "pgoutput",
      "slot.name": "logistics_outbox_slot",
      "publication.autocreate.mode": "filtered",
      "transforms": "outbox",
      "transforms.outbox.type": "io.debezium.transforms.outbox.EventRouter",
      "transforms.outbox.table.field.event.id": "id",
      "transforms.outbox.table.field.event.key": "aggregate_id",
      "transforms.outbox.table.field.event.payload": "payload",
      "transforms.outbox.route.by.field": "aggregate_type",
      "transforms.outbox.table.fields.additional.placement": "event_type:header"
    }
  }'

curl -s -X POST http://debezium:8083/connectors \
  -H "Content-Type: application/json" \
  -d '{
    "name": "wallet-outbox-connector",
    "config": {
      "connector.class": "io.debezium.connector.postgresql.PostgresConnector",
      "tasks.max": "1",
      "database.hostname": "postgres",
      "database.port": "5432",
      "database.user": "'"${POSTGRES_USER}"'",
      "database.password": "'"${POSTGRES_PASSWORD}"'",
      "database.dbname": "wallet_db",
      "topic.prefix": "wallet",
      "table.include.list": "public.outbox_events",
      "plugin.name": "pgoutput",
      "slot.name": "wallet_outbox_slot",
      "publication.autocreate.mode": "filtered",
      "transforms": "outbox",
      "transforms.outbox.type": "io.debezium.transforms.outbox.EventRouter",
      "transforms.outbox.table.field.event.id": "id",
      "transforms.outbox.table.field.event.key": "aggregate_id",
      "transforms.outbox.table.field.event.payload": "payload",
      "transforms.outbox.route.by.field": "aggregate_type",
      "transforms.outbox.table.fields.additional.placement": "event_type:header"
    }
  }'

echo "Debezium connectors registered."
