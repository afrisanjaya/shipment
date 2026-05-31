from __future__ import annotations

import argparse
import io
import json
import logging
import os
import signal
import sys
import time
from datetime import datetime, timezone
from pathlib import Path
from typing import Optional

import pyarrow as pa
import pyarrow.parquet as pq
from kafka import KafkaConsumer
from minio import Minio

logger = logging.getLogger(__name__)


DEFAULT_BOOTSTRAP_SERVERS = os.environ.get("KAFKA_BROKERS", "localhost:29092")
DEFAULT_MINIO_ENDPOINT = os.environ.get("MINIO_ENDPOINT", "localhost:9000")
DEFAULT_MINIO_ACCESS_KEY = os.environ.get("MINIO_ROOT_USER", "minioadmin")
DEFAULT_MINIO_SECRET_KEY = os.environ.get("MINIO_ROOT_PASSWORD", "minioadmin")
DEFAULT_MINIO_BUCKET = os.environ.get("MINIO_BUCKET", "Shipment-datalake")
DEFAULT_BATCH_SIZE = int(os.environ.get("PARQUET_BATCH_SIZE", "2000"))
DEFAULT_FLUSH_INTERVAL = int(os.environ.get("PARQUET_FLUSH_INTERVAL", "60"))

TOPICS = ["sensor-reading", "shipment-gps-ping"]


_minio_client: Optional[Minio] = None


def get_minio_client(endpoint: str, access_key: str, secret_key: str) -> Minio:
    global _minio_client
    if _minio_client is None:
        host, port = _parse_endpoint(endpoint)
        _minio_client = Minio(
            f"{host}:{port}",
            access_key=access_key,
            secret_key=secret_key,
            secure=False,
        )
        _ensure_bucket(_minio_client)
    return _minio_client


def _parse_endpoint(endpoint: str) -> tuple[str, int]:
    if ":" in endpoint:
        host, port = endpoint.rsplit(":", 1)
        return host, int(port)
    return endpoint, 9000


def _ensure_bucket(client: Minio) -> None:
    found = client.bucket_exists(DEFAULT_MINIO_BUCKET)
    if not found:
        client.make_bucket(DEFAULT_MINIO_BUCKET)
        logger.info("Created MinIO bucket: %s", DEFAULT_MINIO_BUCKET)
    else:
        logger.debug("MinIO bucket already exists: %s", DEFAULT_MINIO_BUCKET)



class ParquetBatcher:
    """Accumulates Kafka records and flushes to MinIO as Parquet files."""

    def __init__(
        self,
        minio_client: Minio,
        bucket: str,
        batch_size: int,
        flush_interval: int,
    ):
        self.minio = minio_client
        self.bucket = bucket
        self.batch_size = batch_size
        self.flush_interval = flush_interval

        self._sensor_buffer: list[dict] = []
        self._gps_buffer: list[dict] = []
        self._last_flush = time.monotonic()

    def add_sensor(self, record: dict) -> None:
        self._sensor_buffer.append(record)
        if len(self._sensor_buffer) >= self.batch_size:
            self._flush_topic("sensor-reading", self._sensor_buffer)
            self._sensor_buffer.clear()

    def add_gps(self, record: dict) -> None:
        self._gps_buffer.append(record)
        if len(self._gps_buffer) >= self.batch_size:
            self._flush_topic("shipment-gps-ping", self._gps_buffer)
            self._gps_buffer.clear()

    def timed_flush(self) -> None:
        """Flush any pending data if the flush interval has elapsed."""
        now = time.monotonic()
        if now - self._last_flush < self.flush_interval:
            return

        if self._sensor_buffer:
            self._flush_topic("sensor-reading", self._sensor_buffer)
            self._sensor_buffer.clear()

        if self._gps_buffer:
            self._flush_topic("shipment-gps-ping", self._gps_buffer)
            self._gps_buffer.clear()

        self._last_flush = now

    def force_flush(self) -> None:
        """Flush all remaining data on shutdown."""
        if self._sensor_buffer:
            self._flush_topic("sensor-reading", self._sensor_buffer)
            self._sensor_buffer.clear()
        if self._gps_buffer:
            self._flush_topic("shipment-gps-ping", self._gps_buffer)
            self._gps_buffer.clear()

    def _flush_topic(self, topic: str, records: list[dict]) -> None:
        if not records:
            return

        try:
            table = pa.Table.from_pylist(records)
            buf = io.BytesIO()
            pq.write_table(table, buf, compression="snappy")
            buf.seek(0)

            timestamp = datetime.now(timezone.utc).strftime("%Y/%m/%d/%H")
            object_key = f"{topic}/{timestamp}/{_generate_filename(topic)}"
            self.minio.put_object(
                self.bucket,
                object_key,
                buf,
                length=buf.getbuffer().nbytes,
                content_type="application/octet-stream",
            )

            logger.info(
                "Flushed %d records → s3://%s/%s (%.1f KB)",
                len(records),
                self.bucket,
                object_key,
                buf.getbuffer().nbytes / 1024,
            )
        except Exception:
            logger.exception("Failed to flush %d records for topic %s", len(records), topic)


def _generate_filename(topic: str) -> str:
    ts = datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%S")
    return f"{topic.replace('-', '_')}_{ts}.parquet"



def main() -> None:
    parser = argparse.ArgumentParser(
        description="Kafka → Parquet → MinIO batch daemon"
    )
    parser.add_argument(
        "--bootstrap-servers", default=DEFAULT_BOOTSTRAP_SERVERS,
        help="Kafka bootstrap servers"
    )
    parser.add_argument(
        "--minio-endpoint", default=DEFAULT_MINIO_ENDPOINT,
        help="MinIO endpoint (host:port)"
    )
    parser.add_argument(
        "--batch-size", type=int, default=DEFAULT_BATCH_SIZE,
        help="Records per Parquet file"
    )
    parser.add_argument(
        "--flush-interval", type=int, default=DEFAULT_FLUSH_INTERVAL,
        help="Max seconds between forced flushes"
    )
    args = parser.parse_args()

    logging.basicConfig(
        level=logging.INFO,
        format="%(asctime)s [%(levelname)s] %(name)s: %(message)s",
        datefmt="%H:%M:%S",
    )

    logger.info("Starting Parquet Writer daemon")
    logger.info("  Kafka: %s", args.bootstrap_servers)
    logger.info("  MinIO: %s", args.minio_endpoint)
    logger.info("  Topics: %s", TOPICS)
    logger.info("  Batch size: %d | Flush interval: %ds", args.batch_size, args.flush_interval)

    minio_client = get_minio_client(
        args.minio_endpoint, DEFAULT_MINIO_ACCESS_KEY, DEFAULT_MINIO_SECRET_KEY
    )

    consumer = KafkaConsumer(
        *TOPICS,
        bootstrap_servers=args.bootstrap_servers,
        group_id="parquet-writer",
        auto_offset_reset="earliest",
        enable_auto_commit=True,
        value_deserializer=lambda m: json.loads(m.decode("utf-8")),
        consumer_timeout_ms=5000,
    )

    batcher = ParquetBatcher(minio_client, DEFAULT_MINIO_BUCKET,
                             args.batch_size, args.flush_interval)

    running = True

    def handle_signal(signum, frame):
        nonlocal running
        logger.info("Received signal %s — shutting down...", signum)
        running = False

    signal.signal(signal.SIGINT, handle_signal)
    signal.signal(signal.SIGTERM, handle_signal)

    try:
        while running:
            records_polled = 0
            for msg in consumer:
                records_polled += 1
                if msg.topic == "sensor-reading":
                    batcher.add_sensor(msg.value)
                elif msg.topic == "shipment-gps-ping":
                    batcher.add_gps(msg.value)

            batcher.timed_flush()

            if records_polled == 0:
                pass

    except KeyboardInterrupt:
        logger.info("KeyboardInterrupt received")
    finally:
        logger.info("Flushing remaining records...")
        batcher.force_flush()
        consumer.close()
        logger.info("Parquet Writer shut down cleanly.")


if __name__ == "__main__":
    main()
