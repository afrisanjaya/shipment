import json
import os
from datetime import datetime, timezone, timedelta

import boto3
from boto3.dynamodb.types import TypeDeserializer

S3_BUCKET = os.environ.get("S3_BUCKET", "Shipment-logistics-catalog")
SENSOR_TABLE = os.environ.get("SENSOR_TABLE", "sensor_readings")
TRACKING_TABLE = os.environ.get("TRACKING_TABLE", "shipment_tracking")

dynamodb = boto3.client("dynamodb")
s3 = boto3.client("s3")
deserializer = TypeDeserializer()


def handler(event, context):
    """Entry point — called every 15 minutes by EventBridge."""
    now = datetime.now(timezone.utc)
    results = {
        "run_at": now.isoformat(),
        "active_shipments": active_shipments(now),
        "cold_chain_anomalies": cold_chain_anomalies(now),
        "fleet_summary": fleet_summary(now),
    }

    key = f"analytics/{now.strftime('%Y/%m/%d/%H%M')}.json"
    s3.put_object(
        Bucket=S3_BUCKET,
        Key=key,
        Body=json.dumps(results, indent=2, default=str),
        ContentType="application/json",
    )
    print(f"[ANALYTICS] Written to s3://{S3_BUCKET}/{key}")
    return {"status": "ok", "key": key}



def query_table(table: str, key_condition: str, expr_values: dict,
                limit: int = 100, index: str | None = None) -> list[dict]:
    """Query a DynamoDB table and return deserialized items."""
    kwargs = {
        "TableName": table,
        "KeyConditionExpression": key_condition,
        "ExpressionAttributeValues": {
            k: _serialize(v) for k, v in expr_values.items()
        },
        "Limit": limit,
        "ScanIndexForward": False,
    }
    if index:
        kwargs["IndexName"] = index

    resp = dynamodb.query(**kwargs)
    return [_deserialize(item) for item in resp.get("Items", [])]


def scan_table(table: str, filter_expr: str | None = None,
               expr_values: dict | None = None) -> list[dict]:
    """Scan a DynamoDB table (use sparingly — for small tables only)."""
    kwargs = {"TableName": table, "Limit": 500}
    if filter_expr:
        kwargs["FilterExpression"] = filter_expr
    if expr_values:
        kwargs["ExpressionAttributeValues"] = {
            k: _serialize(v) for k, v in expr_values.items()
        }
    resp = dynamodb.scan(**kwargs)
    return [_deserialize(item) for item in resp.get("Items", [])]



def active_shipments(now: datetime) -> dict:
    """Count shipments with GPS pings in the last 30 minutes."""
    cutoff = (now - timedelta(minutes=30)).isoformat()
    items = scan_table(TRACKING_TABLE)
    recent = [i for i in items if i.get("timestamp", "") >= cutoff]
    active_ids = set(i.get("shipment_id") for i in recent)
    return {"active_count": len(active_ids), "ping_count": len(recent),
            "window_minutes": 30, "shipment_ids": list(active_ids)[:20]}


def cold_chain_anomalies(now: datetime) -> dict:
    """Find sensor readings that are out of range in the last 24 hours."""
    cutoff = (now - timedelta(hours=24)).isoformat()
    items = scan_table(SENSOR_TABLE)
    anomalies = []
    all_readings = []
    for item in items:
        ts = item.get("timestamp", "")
        if ts < cutoff:
            continue
        all_readings.append(item)
        if not item.get("is_in_range", True):
            anomalies.append({
                "shipment_id": item.get("shipment_id"),
                "sensor_type": item.get("sensor_type"),
                "value": item.get("value"),
                "unit": item.get("unit"),
                "threshold_min": item.get("threshold_min"),
                "threshold_max": item.get("threshold_max"),
                "timestamp": ts,
            })

    return {"total_readings_24h": len(all_readings),
            "anomaly_count": len(anomalies),
            "anomaly_pct": round(len(anomalies) / max(len(all_readings), 1) * 100, 1),
            "anomalies": anomalies[:20]}


def fleet_summary(now: datetime) -> dict:
    """Summarize fleet activity from the last hour."""
    cutoff = (now - timedelta(hours=1)).isoformat()
    items = scan_table(TRACKING_TABLE)
    recent = [i for i in items if i.get("timestamp", "") >= cutoff]
    shipments = {}
    for p in recent:
        sid = p.get("shipment_id")
        if sid not in shipments:
            shipments[sid] = []
        shipments[sid].append(p)

    return {
        "total_shipments_tracked": len(shipments),
        "total_pings_1h": len(recent),
        "avg_speed_kph": _avg([p.get("speed_kph", 0) or 0 for p in recent]),
        "top_shipments_by_pings": sorted(
            [(sid, len(pings)) for sid, pings in shipments.items()],
            key=lambda x: x[1], reverse=True
        )[:5],
    }



def _serialize(val) -> dict:
    """Serialize a Python value to DynamoDB AttributeValue dict."""
    if isinstance(val, str):
        return {"S": val}
    if isinstance(val, (int, float)):
        return {"N": str(val)}
    raise ValueError(f"Cannot serialize {type(val)}: {val}")


def _deserialize(item: dict) -> dict:
    return {k: deserializer.deserialize(v) for k, v in item.items()}


def _avg(values: list) -> float | None:
    if not values:
        return None
    return round(sum(float(v) for v in values) / len(values), 1)



if __name__ == "__main__":
    print("[ANALYTICS] Running local analytics against DynamoDB...")
    result = handler(None, None)
    print(f"[ANALYTICS] Done: {json.dumps(result)}")
