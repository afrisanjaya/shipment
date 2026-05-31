from __future__ import annotations

import argparse
import logging
import os
import sys
from datetime import datetime, timedelta, timezone

import duckdb

logger = logging.getLogger(__name__)


DEFAULT_MINIO_ENDPOINT = os.environ.get("MINIO_ENDPOINT", "localhost:9000")
DEFAULT_MINIO_ACCESS_KEY = os.environ.get("MINIO_ROOT_USER", "minioadmin")
DEFAULT_MINIO_SECRET_KEY = os.environ.get("MINIO_ROOT_PASSWORD", "minioadmin")
DEFAULT_BUCKET = os.environ.get("MINIO_BUCKET", "Shipment-datalake")

S3_PATH = f"s3://{DEFAULT_BUCKET}"



def setup_duckdb(endpoint: str, access_key: str, secret_key: str) -> duckdb.DuckDBPyConnection:
    """Configure DuckDB with MinIO S3-compatible endpoint."""
    conn = duckdb.connect(":memory:")

    conn.execute("INSTALL httpfs;")
    conn.execute("LOAD httpfs;")

    conn.execute(f"SET s3_endpoint='{endpoint}';")
    conn.execute(f"SET s3_access_key_id='{access_key}';")
    conn.execute(f"SET s3_secret_access_key='{secret_key}';")
    conn.execute("SET s3_use_ssl=false;")
    conn.execute("SET s3_url_style='path';")

    logger.info("DuckDB connected to MinIO: %s", endpoint)
    return conn



def query_active_shipments(conn: duckdb.DuckDBPyConnection) -> list:
    """Active shipments with latest GPS coordinates."""
    return conn.execute(f"""
        SELECT
            shipment_id,
            MAX(timestamp) AS last_seen,
            FIRST(latitude ORDER BY timestamp DESC) AS current_lat,
            FIRST(longitude ORDER BY timestamp DESC) AS current_lon,
            FIRST(speed_kmh ORDER BY timestamp DESC) AS current_speed_kmh,
            COUNT(*) AS ping_count_24h
        FROM read_parquet('{S3_PATH}/shipment-gps-ping/*/*/*/*.parquet')
        WHERE CAST(timestamp AS TIMESTAMP) > NOW() - INTERVAL 24 HOURS
        GROUP BY shipment_id
        ORDER BY last_seen DESC
        LIMIT 20
    """).fetchall()


def query_cold_chain_anomalies(conn: duckdb.DuckDBPyConnection) -> list:
    """Sensor readings flagged as out-of-range (cold chain compliance)."""
    return conn.execute(f"""
        SELECT
            shipment_id,
            sensor_type,
            COUNT(*) AS total_readings,
            COUNT(*) FILTER (WHERE NOT is_in_range) AS out_of_range_count,
            ROUND(
                100.0 * COUNT(*) FILTER (WHERE NOT is_in_range) / COUNT(*), 2
            ) AS anomaly_pct,
            MIN(value) AS min_value,
            MAX(value) AS max_value,
            AVG(value) AS avg_value
        FROM read_parquet('{S3_PATH}/sensor-reading/*/*/*/*.parquet')
        WHERE CAST(timestamp AS TIMESTAMP) > NOW() - INTERVAL 7 DAYS
        GROUP BY shipment_id, sensor_type
        HAVING COUNT(*) FILTER (WHERE NOT is_in_range) > 0
        ORDER BY anomaly_pct DESC
        LIMIT 20
    """).fetchall()


def query_warehouse_throughput(conn: duckdb.DuckDBPyConnection) -> list:
    """Daily shipment volume by warehouse — from GPS tracking data."""
    return conn.execute(f"""
        SELECT
            warehouse_id,
            DATE_TRUNC('day', CAST(timestamp AS TIMESTAMP)) AS day,
            COUNT(DISTINCT shipment_id) AS active_shipments,
            COUNT(*) AS gps_pings,
            AVG(speed_kmh) AS avg_speed_kmh,
            PERCENTILE_CONT(0.95) WITHIN GROUP (ORDER BY speed_kmh) AS p95_speed_kmh
        FROM read_parquet('{S3_PATH}/shipment-gps-ping/*/*/*/*.parquet')
        WHERE CAST(timestamp AS TIMESTAMP) > NOW() - INTERVAL 7 DAYS
        GROUP BY warehouse_id, day
        ORDER BY day DESC, active_shipments DESC
        LIMIT 30
    """).fetchall()


def query_fleet_utilization(conn: duckdb.DuckDBPyConnection) -> list:
    """Fleet utilization: active couriers and distance traveled."""
    return conn.execute(f"""
        SELECT
            courier_id,
            COUNT(*) AS gps_pings,
            COUNT(DISTINCT shipment_id) AS deliveries,
            ROUND(AVG(speed_kmh), 1) AS avg_speed_kmh,
            ROUND(MAX(speed_kmh), 1) AS max_speed_kmh,
            MAX(CAST(timestamp AS TIMESTAMP)) AS last_active
        FROM read_parquet('{S3_PATH}/shipment-gps-ping/*/*/*/*.parquet')
        WHERE CAST(timestamp AS TIMESTAMP) > NOW() - INTERVAL 24 HOURS
        GROUP BY courier_id
        ORDER BY deliveries DESC
        LIMIT 20
    """).fetchall()


def query_data_lake_stats(conn: duckdb.DuckDBPyConnection) -> list:
    """Overall data lake statistics — file count and row counts."""
    return conn.execute(f"""
        WITH gps_stats AS (
            SELECT
                'shipment-gps-ping' AS topic,
                COUNT(*) AS total_rows,
                COUNT(DISTINCT shipment_id) AS unique_shipments,
                MIN(CAST(timestamp AS TIMESTAMP)) AS earliest,
                MAX(CAST(timestamp AS TIMESTAMP)) AS latest
            FROM read_parquet('{S3_PATH}/shipment-gps-ping/*/*/*/*.parquet')
        ),
        sensor_stats AS (
            SELECT
                'sensor-reading' AS topic,
                COUNT(*) AS total_rows,
                COUNT(DISTINCT shipment_id) AS unique_shipments,
                MIN(CAST(timestamp AS TIMESTAMP)) AS earliest,
                MAX(CAST(timestamp AS TIMESTAMP)) AS latest
            FROM read_parquet('{S3_PATH}/sensor-reading/*/*/*/*.parquet')
        )
        SELECT * FROM gps_stats
        UNION ALL
        SELECT * FROM sensor_stats
        ORDER BY topic
    """).fetchall()



QUERIES = {
    "active":    ("Active Shipments (GPS)", query_active_shipments),
    "anomaly":   ("Cold Chain Anomalies",  query_cold_chain_anomalies),
    "warehouse": ("Warehouse Throughput",  query_warehouse_throughput),
    "fleet":     ("Fleet Utilization",     query_fleet_utilization),
    "stats":     ("Data Lake Stats",       query_data_lake_stats),
}


def main() -> None:
    parser = argparse.ArgumentParser(
        description="DuckDB analytical queries on Shipment Data Lake (MinIO + Parquet)"
    )
    parser.add_argument(
        "--minio-endpoint", default=DEFAULT_MINIO_ENDPOINT,
        help="MinIO endpoint (default: localhost:9000)"
    )
    parser.add_argument(
        "--query", choices=list(QUERIES.keys()) + ["all"],
        default="all",
        help="Specific query to run (default: all)"
    )
    args = parser.parse_args()

    logging.basicConfig(
        level=logging.INFO,
        format="%(asctime)s [%(levelname)s] %(message)s",
        datefmt="%H:%M:%S",
    )

    conn = setup_duckdb(
        args.minio_endpoint,
        DEFAULT_MINIO_ACCESS_KEY,
        DEFAULT_MINIO_SECRET_KEY,
    )

    queries_to_run = (
        list(QUERIES.items()) if args.query == "all"
        else [(args.query, QUERIES[args.query])]
    )

    print(f"\n{'='*80}")
    print("  Shipment Data Lake — DuckDB Analytics")
    print(f"  MinIO: {args.minio_endpoint}  |  Bucket: {DEFAULT_BUCKET}")
    print(f"{'='*80}\n")

    for name, (title, fn) in queries_to_run:
        print(f"── {title} ──")
        try:
            rows = fn(conn)
            if not rows:
                print("  (no data — run parquet_writer.py first)\n")
                continue

            col_names = [desc[0] for desc in fn(conn).description] if rows else []
            if hasattr(fn(conn), 'description'):
                pass

            for row in rows:
                values = [
                    str(v)[:60] if isinstance(v, str) else
                    f"{v:.2f}" if isinstance(v, float) else
                    str(v)
                    for v in row
                ]
                print(f"  {' | '.join(values)}")
            print()
        except Exception as e:
            print(f"  Query failed: {e}")
            print(f"  (Does the parquet path exist in MinIO? Run parquet_writer.py first.)\n")

    conn.close()
    print("Done.")


if __name__ == "__main__":
    main()
