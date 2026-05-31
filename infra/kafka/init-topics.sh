#!/bin/bash
echo "Starting Kafka"
cub kafka-ready -b kafka:9092 1 20

kafka-topics --bootstrap-server kafka:9092 --create --if-not-exists \
  --topic wallet-topped-up --partitions 3 --replication-factor 1

kafka-topics --bootstrap-server kafka:9092 --create --if-not-exists \
  --topic invoice-paid --partitions 3 --replication-factor 1

kafka-topics --bootstrap-server kafka:9092 --create --if-not-exists \
  --topic invoice-failed --partitions 3 --replication-factor 1

kafka-topics --bootstrap-server kafka:9092 --create --if-not-exists \
  --topic shipment-created --partitions 3 --replication-factor 1

kafka-topics --bootstrap-server kafka:9092 --create --if-not-exists \
  --topic shipment-dispatched --partitions 3 --replication-factor 1

kafka-topics --bootstrap-server kafka:9092 --create --if-not-exists \
  --topic shipment-cancelled --partitions 3 --replication-factor 1

kafka-topics --bootstrap-server kafka:9092 --create --if-not-exists \
  --topic shipment-delivered --partitions 3 --replication-factor 1

kafka-topics --bootstrap-server kafka:9092 --create --if-not-exists \
  --topic wallet-events --partitions 3 --replication-factor 1

kafka-topics --bootstrap-server kafka:9092 --create --if-not-exists \
  --topic rush-order-results --partitions 3 --replication-factor 1

kafka-topics --bootstrap-server kafka:9092 --create --if-not-exists \
  --topic shipment-gps-ping --partitions 3 --replication-factor 1

kafka-topics --bootstrap-server kafka:9092 --create --if-not-exists \
  --topic sensor-reading --partitions 3 --replication-factor 1

kafka-topics --bootstrap-server kafka:9092 --create --if-not-exists \
  --topic sla-breach --partitions 3 --replication-factor 1

echo "Kafka topics created."
