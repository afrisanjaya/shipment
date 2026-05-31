"""
GPS & Sensor Simulator for Shipment Supply Chain Platform.

Publishes to Kafka topics:
  - shipment-gps-ping  (every 30s per shipment)
  - sensor-reading     (every 60s per cold chain shipment)
  - sla-breach         (when shipment is past estimated delivery)
"""
import json
import os
import random
import time
import uuid
from datetime import datetime, timezone

from kafka import KafkaProducer
from kafka.errors import NoBrokersAvailable

KAFKA_BROKERS = os.environ.get("KAFKA_BROKERS", "localhost:29092")
NUM_SHIPMENTS = int(os.environ.get("NUM_SHIPMENTS", "10"))
COLD_CHAIN_RATIO = float(os.environ.get("COLD_CHAIN_RATIO", "0.3"))
GPS_INTERVAL_SEC = int(os.environ.get("GPS_INTERVAL_SEC", "30"))
SENSOR_INTERVAL_SEC = int(os.environ.get("SENSOR_INTERVAL_SEC", "60"))

DEMO_TENANT_ID = "a1b2c3d4-e5f6-7890-abcd-ef1234567801"

ROUTES = [
    {"name": "Jakarta → Surabaya",    "from": (-6.2088, 106.8456), "to": (-7.2575, 112.7521)},
    {"name": "Bandung → Jakarta",     "from": (-6.9175, 107.6191), "to": (-6.2088, 106.8456)},
    {"name": "Jakarta → Medan",       "from": (-6.2088, 106.8456), "to": (3.5952, 98.6722)},
    {"name": "Surabaya → Makassar",   "from": (-7.2575, 112.7521), "to": (-5.1477, 119.4327)},
    {"name": "Jakarta → Bandung",     "from": (-6.2088, 106.8456), "to": (-6.9175, 107.6191)},
    {"name": "Surabaya → Denpasar",   "from": (-7.2575, 112.7521), "to": (-8.6705, 115.2126)},
    {"name": "Jakarta → Semarang",    "from": (-6.2088, 106.8456), "to": (-6.9667, 110.4167)},
    {"name": "Medan → Palembang",     "from": (3.5952, 98.6722),   "to": (-2.9761, 104.7754)},
    {"name": "Makassar → Manado",     "from": (-5.1477, 119.4327), "to": (1.4748, 124.8421)},
    {"name": "Bandung → Yogyakarta",  "from": (-6.9175, 107.6191), "to": (-7.7956, 110.3695)},
]


def create_producer() -> KafkaProducer:
    """Connect to Kafka with retry."""
    for attempt in range(30):
        try:
            producer = KafkaProducer(
                bootstrap_servers=KAFKA_BROKERS,
                value_serializer=lambda v: json.dumps(v, default=str).encode("utf-8"),
                key_serializer=lambda k: k.encode("utf-8") if k else None,
            )
            print(f"[GPS-SIM] Connected to Kafka at {KAFKA_BROKERS}")
            return producer
        except NoBrokersAvailable:
            print(f"[GPS-SIM] Waiting for Kafka... attempt {attempt + 1}/30")
            time.sleep(5)
    raise RuntimeError("Could not connect to Kafka")


class Shipment:
    """Simulated shipment moving along a route."""

    def __init__(self, shipment_id: str, route: dict, is_cold_chain: bool):
        self.shipment_id = shipment_id
        self.route = route
        self.is_cold_chain = is_cold_chain
        self.progress = 0.0
        self.speed_factor = random.uniform(0.6, 1.4)
        self.started_at = datetime.now(timezone.utc)

    def step(self) -> dict:
        """Advance progress and return current GPS ping."""
        step_ratio = (GPS_INTERVAL_SEC * self.speed_factor) / random.uniform(43200, 259200)
        self.progress = min(self.progress + step_ratio, 1.0)

        route = self.route
        lat = route["from"][0] + (route["to"][0] - route["from"][0]) * self.progress
        lon = route["from"][1] + (route["to"][1] - route["from"][1]) * self.progress

        return {
            "shipmentId": self.shipment_id,
            "tenantId": DEMO_TENANT_ID,
            "timestamp": datetime.now(timezone.utc).isoformat(),
            "latitude": round(lat + random.gauss(0, 0.001), 6),
            "longitude": round(lon + random.gauss(0, 0.001), 6),
            "speedKph": round(random.uniform(40, 90) * self.speed_factor, 1),
            "heading": round(random.uniform(0, 360), 1),
            "accuracy": round(random.uniform(3, 15), 1),
        }

    @property
    def is_complete(self) -> bool:
        return self.progress >= 1.0


def generate_sensor_reading(shipment: Shipment) -> dict:
    """Generate a temperature or humidity sensor reading for cold chain."""
    sensor_type = random.choice(["TEMPERATURE", "HUMIDITY"])
    if sensor_type == "TEMPERATURE":
        value = round(random.gauss(4.5, 1.5), 1)
        unit = "celsius"
        threshold_min, threshold_max = 2.0, 8.0
    else:
        value = round(random.gauss(52, 10), 1)
        unit = "percent"
        threshold_min, threshold_max = 40.0, 65.0

    is_in_range = threshold_min <= value <= threshold_max

    return {
        "shipmentId": shipment.shipment_id,
        "tenantId": DEMO_TENANT_ID,
        "timestamp": datetime.now(timezone.utc).isoformat(),
        "sensorType": sensor_type,
        "value": value,
        "unit": unit,
        "thresholdMin": threshold_min,
        "thresholdMax": threshold_max,
        "isInRange": is_in_range,
    }


def main():
    print(f"[GPS-SIM] Starting: {NUM_SHIPMENTS} shipments, cold chain ratio={COLD_CHAIN_RATIO}")
    producer = create_producer()

    shipments = []
    for i in range(NUM_SHIPMENTS):
        route = random.choice(ROUTES)
        is_cold = random.random() < COLD_CHAIN_RATIO
        sid = str(uuid.uuid4())
        shipments.append(Shipment(sid, route, is_cold))
        label = "[COLD] " if is_cold else ""
        print(f"[GPS-SIM] Shipment {label}{sid[:8]}... on route: {route['name']}")

    last_sensor_ts = time.time()
    ticks = 0

    try:
        while True:
            ticks += 1
            gps_count = 0
            sensor_count = 0

            for s in shipments:
                if s.is_complete:
                    continue

                ping = s.step()
                producer.send("shipment-gps-ping", key=s.shipment_id, value=ping)
                gps_count += 1

                if s.progress > 0.95 and random.random() < 0.1:
                    producer.send("sla-breach", key=s.shipment_id, value={
                        "shipmentId": s.shipment_id,
                        "tenantId": DEMO_TENANT_ID,
                        "reason": "Estimated delivery time exceeded",
                        "progress": round(s.progress, 3),
                        "timestamp": datetime.now(timezone.utc).isoformat(),
                    })

            if time.time() - last_sensor_ts >= SENSOR_INTERVAL_SEC:
                for s in shipments:
                    if s.is_cold_chain and not s.is_complete:
                        reading = generate_sensor_reading(s)
                        producer.send("sensor-reading", key=s.shipment_id, value=reading)
                        sensor_count += 1

                        if not reading["isInRange"]:
                            producer.send("sla-breach", key=s.shipment_id, value={
                                "shipmentId": s.shipment_id,
                                "tenantId": DEMO_TENANT_ID,
                                "reason": f"Sensor out of range: {reading['sensorType']}={reading['value']}{reading['unit']}",
                                "timestamp": datetime.now(timezone.utc).isoformat(),
                            })

                last_sensor_ts = time.time()

            for i, s in enumerate(shipments):
                if s.is_complete:
                    route = random.choice(ROUTES)
                    is_cold = random.random() < COLD_CHAIN_RATIO
                    sid = str(uuid.uuid4())
                    shipments[i] = Shipment(sid, route, is_cold)
                    label = "[COLD] " if is_cold else ""
                    print(f"[GPS-SIM] Completed -> New shipment {label}{sid[:8]}... on: {route['name']}")

            total = sum(1 for s in shipments if not s.is_complete)
            print(f"[GPS-SIM] Tick {ticks}: {gps_count} GPS pings, {sensor_count} sensor readings | {total} active")

            time.sleep(GPS_INTERVAL_SEC)

    except KeyboardInterrupt:
        print("[GPS-SIM] Shutting down...")
    finally:
        producer.flush()
        producer.close()


if __name__ == "__main__":
    main()
