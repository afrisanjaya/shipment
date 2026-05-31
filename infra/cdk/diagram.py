from diagrams import Diagram, Cluster, Edge
from diagrams.aws.compute import EC2, Lambda
from diagrams.aws.database import RDS, Dynamodb, ElasticacheCacheNode
from diagrams.aws.network import (
    VPC, PrivateSubnet, InternetGateway, NATGateway,
    Route53,
)
from diagrams.aws.storage import S3
from diagrams.aws.integration import SQS, Eventbridge
from diagrams.aws.security import SecretsManager, IAMRole
from diagrams.aws.analytics import Athena, Glue
from diagrams.aws.ml import Bedrock
from diagrams.aws.general import User
from diagrams.aws.management import Cloudwatch
from diagrams.onprem.queue import Kafka
from diagrams.onprem.database import Postgresql
from diagrams.onprem.monitoring import Prometheus as PrometheusOnPrem, Grafana as GrafanaOnPrem
from diagrams.onprem.logging import Loki
from diagrams.onprem.inmemory import Redis as RedisOnPrem
from diagrams.onprem.network import Zookeeper
from diagrams.programming.language import Go, Java, Python

graph_attr = {
    "fontsize": "18",
    "bgcolor": "#ffffff",
    "pad": "0.5",
    "splines": "polyline",
    "nodesep": "0.8",
    "ranksep": "1.2",
}

edge_attr = {
    "fontsize": "9",
    "color": "#7b7b7b",
}

node_attr = {
    "fontsize": "10",
}


def create_diagram():
    with Diagram(
        "Shipment — Supply Chain & Logistics Platform",
        filename="infra/cdk/shipment_aws_architecture",
        outformat=["png", "dot"],
        show=False,
        direction="TB",
        graph_attr=graph_attr,
        edge_attr=edge_attr,
        node_attr=node_attr,
    ):
        client = User("Client App / Mobile")
        admin = User("Operations Staff")

        with Cluster("AWS Cloud"):
            dns = Route53("Route 53\nDNS")

            with Cluster("VPC — shipment-vpc (10.0.0.0/16, 2 AZs)"):
                igw = InternetGateway("Internet Gateway")

                with Cluster("Public Subnets (dmz — /24)"):
                    nat = NATGateway("NAT Gateway\n(1 instance)")
                    compute_ec2 = EC2("EC2 Compute\nt3.medium\n(Docker Compose)")

                with Cluster("Private Subnets (app tier — /24)"):
                    _ = PrivateSubnet("Services ECS\n(planned)")

                with Cluster("Isolated Subnets (data tier — /28)"):
                    _ = PrivateSubnet("Database Layer\n(no internet)")

                igw >> Edge(label="ingress") >> nat
                igw >> Edge(label="SSH/Grafana", color="darkgreen") >> compute_ec2

            with Cluster("Database Layer — DatabaseStack"):
                with Cluster("RDS PostgreSQL 16"):
                    booking_rds = RDS("Booking DB\n(booking_db)")
                    logistics_rds = RDS("Logistics DB\n(logistics_db)")
                    wallet_rds = RDS("Wallet DB\n(wallet_db)")

                with Cluster("ElastiCache Redis 7.1"):
                    elasticache_redis = ElasticacheCacheNode("Redis\nFlash Sale +\nSession Cache")

                secrets_mgr = SecretsManager("Secrets Manager\nshipment/booking-db\nshipment/logistics-db\nshipment/wallet-db")

            with Cluster("Storage & Messaging — AwsResourcesStack"):
                with Cluster("S3 Data Lake"):
                    data_lake = S3("Data Lake\nshipment-logistics-catalog")

                with Cluster("DynamoDB Time-Series"):
                    sensor_table = Dynamodb("Sensor Readings\n(sensor_readings)")
                    tracking_table = Dynamodb("Shipment Tracking\n(shipment_tracking)")

                with Cluster("SQS FIFO"):
                    wallet_queue = SQS("Wallet Transactions\n(wallet-transactions.fifo)")
                    wallet_dlq = SQS("Wallet DLQ\n(wallet-transactions-dlq.fifo)")

            with Cluster("Analytics Pipeline"):
                eventbridge = Eventbridge("EventBridge\n(Every 15 min)")
                analytics_lambda = Lambda("Supply Chain Analytics\n(Python 3.12, 256MB)")

                with Cluster("AWS Glue & Athena"):
                    glue_crawler = Glue("Glue Crawler\nDocument Analytics")
                    glue_catalog = Glue("Glue Catalog\n(shipment_logistics_dw)")
                    athena_wg = Athena("Athena\nLogisticsAnalytics\nWorkgroup")

            with Cluster("AI/ML Layer — BedrockStack"):
                bedrock_agent = Bedrock("Bedrock Agent\n(Titan Embeddings V2)")
                kb = Bedrock("Knowledge Base\n(RAG on S3 Vector)")
                s3_vector = S3("S3 Vector Bucket\n(Vector Store)")

            with Cluster("Compute Instance — EC2 t3.medium (Docker Compose)"):
                with Cluster("Java Microservices (Spring Boot)"):
                    logistics_svc = Java("Logistics Service\n:8081\nShipment lifecycle")
                    wallet_svc = Java("Wallet Service\n:8082\nDouble-entry billing")
                    ai_svc = Java("AI Assistant\n:8083\nBedrock agent")
                    data_platform = Java("Data Platform\n:8085\nDocument pipeline")

                with Cluster("Go Service"):
                    scheduler = Go("Scheduler Service\n:8090\nSLA watchdog +\nRush orders")

                with Cluster("Simulators (Python)"):
                    gps_sim = Python("GPS + Sensor\nSimulator\n→ Kafka")

                with Cluster("Infrastructure Containers"):
                    kafka = Kafka("Kafka\n(Confluent 7.6.0)")
                    zk = Zookeeper("Zookeeper")
                    debezium = EC2("Debezium Connect\n2.7.0 (CDC)")
                    postgres_docker = Postgresql("PostgreSQL 16\nwal_level=logical")
                    redis_docker = RedisOnPrem("Redis 7.2\nFlash sale cache")

                with Cluster("Observability"):
                    prometheus = PrometheusOnPrem("Prometheus\n(:9090)")
                    grafana = GrafanaOnPrem("Grafana\n(:3000)")
                    loki = Loki("Loki + Promtail\n(:3100)")

            with Cluster("AWS Managed Services"):
                bedrock_managed = Bedrock("Amazon Bedrock\nAgent Runtime")
                cloudwatch = Cloudwatch("CloudWatch\nMetrics & Logs")
                iam = IAMRole("IAM Roles\n(EC2, Glue, Lambda,\nBedrock)")

        client >> Edge(label="REST API :8081", color="blue") >> logistics_svc
        client >> Edge(label="REST API :8082", color="blue") >> wallet_svc
        client >> Edge(label="REST API :8083", color="blue") >> ai_svc
        admin >> Edge(label="Grafana :3000", color="green") >> grafana
        admin >> Edge(label="Status queries", color="green") >> logistics_svc

        logistics_svc >> Edge(label="JDBC :5432", color="darkred") >> postgres_docker
        wallet_svc >> Edge(label="JDBC :5432", color="darkred") >> postgres_docker
        data_platform >> Edge(label="JDBC :5432", color="darkred") >> postgres_docker

        postgres_docker >> Edge(label="WAL / Outbox", style="dashed", color="purple") >> debezium
        debezium >> Edge(label="Publish events", color="orange") >> kafka

        kafka >> Edge(label="shipment-events", color="orange") >> scheduler
        kafka >> Edge(label="shipment-events", color="orange") >> logistics_svc
        kafka >> Edge(label="invoice-events", color="orange") >> wallet_svc
        kafka >> Edge(label="sensor-reading", color="orange") >> data_platform

        gps_sim >> Edge(label="shipment-gps-ping\nsensor-reading", color="orange") >> kafka

        scheduler >> Edge(label="DECR / GET", color="red") >> redis_docker

        ai_svc >> Edge(label="InvokeAgent", style="dashed", color="purple") >> bedrock_managed
        bedrock_managed >> Edge(style="dashed", color="purple") >> bedrock_agent
        bedrock_agent >> Edge(label="RAG query", style="dashed", color="purple") >> kb
        kb >> Edge(style="dashed", color="purple") >> s3_vector


        data_platform >> Edge(label="Upload Parquet", color="teal") >> data_lake
        gps_sim >> Edge(label="Write sensor data", style="dashed", color="teal") >> sensor_table
        gps_sim >> Edge(label="Write GPS pings", style="dashed", color="teal") >> tracking_table

        eventbridge >> Edge(label="Trigger 15min", color="darkgreen") >> analytics_lambda
        analytics_lambda >> Edge(label="Read tables", color="darkgreen") >> sensor_table
        analytics_lambda >> Edge(label="Read tables", color="darkgreen") >> tracking_table
        analytics_lambda >> Edge(label="Write results", color="darkgreen") >> data_lake

        glue_crawler >> Edge(label="Crawl docs", color="brown") >> data_lake
        glue_crawler >> Edge(label="Update schema", color="brown") >> glue_catalog
        athena_wg >> Edge(label="SQL queries", color="brown") >> glue_catalog
        athena_wg >> Edge(label="Results →", color="brown") >> data_lake

        wallet_svc >> Edge(label="Enqueue txns", color="darkorange") >> wallet_queue
        wallet_queue >> Edge(label="DLQ", style="dashed", color="darkorange") >> wallet_dlq

        logistics_svc >> Edge(label="metrics", style="dotted", color="gray") >> prometheus
        wallet_svc >> Edge(label="metrics", style="dotted", color="gray") >> prometheus
        postgres_docker >> Edge(label="metrics", style="dotted", color="gray") >> prometheus
        kafka >> Edge(label="metrics", style="dotted", color="gray") >> prometheus
        redis_docker >> Edge(label="metrics", style="dotted", color="gray") >> prometheus
        prometheus >> Edge(label="dashboards", color="darkblue") >> grafana
        loki >> Edge(label="logs", color="darkblue") >> grafana

        compute_ec2 >> Edge(label="JDBC (future)", style="dotted", color="darkred") >> booking_rds
        compute_ec2 >> Edge(label="JDBC (future)", style="dotted", color="darkred") >> logistics_rds
        compute_ec2 >> Edge(label="JDBC (future)", style="dotted", color="darkred") >> wallet_rds
        compute_ec2 >> Edge(label="Redis (future)", style="dotted", color="red") >> elasticache_redis


if __name__ == "__main__":
    create_diagram()
    print("Diagram generated: infra/cdk/shipment_aws_architecture.png")
    print("DOT source also saved: infra/cdk/shipment_aws_architecture.dot")
