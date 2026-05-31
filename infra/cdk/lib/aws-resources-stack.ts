import * as cdk from 'aws-cdk-lib';
import * as s3 from 'aws-cdk-lib/aws-s3';
import * as dynamodb from 'aws-cdk-lib/aws-dynamodb';
import * as sqs from 'aws-cdk-lib/aws-sqs';
import * as lambda from 'aws-cdk-lib/aws-lambda';
import * as events from 'aws-cdk-lib/aws-events';
import * as targets from 'aws-cdk-lib/aws-events-targets';
import * as iam from 'aws-cdk-lib/aws-iam';
import * as glue from 'aws-cdk-lib/aws-glue';
import * as athena from 'aws-cdk-lib/aws-athena';
import { Construct } from 'constructs';

export class AwsResourcesStack extends cdk.Stack {
  constructor(scope: Construct, id: string, props?: cdk.StackProps) {
    super(scope, id, props);

    const dataLakeBucket = new s3.Bucket(this, 'DataLakeBucket', {
      bucketName: 'shipment-logistics-catalog',
      versioned: true,
      encryption: s3.BucketEncryption.S3_MANAGED,
      blockPublicAccess: s3.BlockPublicAccess.BLOCK_ALL,
      removalPolicy: cdk.RemovalPolicy.RETAIN,
      lifecycleRules: [
        {
          id: 'ExpireOldVersions',
          enabled: true,
          noncurrentVersionExpiration: cdk.Duration.days(30),
        },
        {
          id: 'AbortIncompleteMultipartUploads',
          enabled: true,
          abortIncompleteMultipartUploadAfter: cdk.Duration.days(7),
        },
      ],
    });

    const sensorReadingsTable = new dynamodb.TableV2(this, 'SensorReadingsTable', {
      tableName: 'sensor_readings',
      partitionKey: { name: 'shipment_id', type: dynamodb.AttributeType.STRING },
      sortKey: { name: 'timestamp', type: dynamodb.AttributeType.STRING },
      billing: dynamodb.Billing.provisioned({
        readCapacity: dynamodb.Capacity.autoscaled({ maxCapacity: 5, targetUtilizationPercent: 70 }),
        writeCapacity: dynamodb.Capacity.autoscaled({ maxCapacity: 5, targetUtilizationPercent: 70 }),
      }),
      removalPolicy: cdk.RemovalPolicy.RETAIN,
      pointInTimeRecovery: true,
    });

    const shipmentTrackingTable = new dynamodb.TableV2(this, 'ShipmentTrackingTable', {
      tableName: 'shipment_tracking',
      partitionKey: { name: 'shipment_id', type: dynamodb.AttributeType.STRING },
      sortKey: { name: 'timestamp', type: dynamodb.AttributeType.STRING },
      billing: dynamodb.Billing.provisioned({
        readCapacity: dynamodb.Capacity.autoscaled({ maxCapacity: 5, targetUtilizationPercent: 70 }),
        writeCapacity: dynamodb.Capacity.autoscaled({ maxCapacity: 5, targetUtilizationPercent: 70 }),
      }),
      removalPolicy: cdk.RemovalPolicy.RETAIN,
      pointInTimeRecovery: true,
    });

    const walletTransactionQueue = new sqs.Queue(this, 'WalletTransactionQueue', {
      queueName: 'wallet-transactions.fifo',
      fifo: true,
      contentBasedDeduplication: false,
      visibilityTimeout: cdk.Duration.seconds(30),
      retentionPeriod: cdk.Duration.days(4),
      receiveMessageWaitTime: cdk.Duration.seconds(0),
      removalPolicy: cdk.RemovalPolicy.RETAIN,
    });

    const walletDlq = new sqs.Queue(this, 'WalletTransactionDlq', {
      queueName: 'wallet-transactions-dlq.fifo',
      fifo: true,
      retentionPeriod: cdk.Duration.days(14),
      removalPolicy: cdk.RemovalPolicy.RETAIN,
    });

    const analyticsLambda = new lambda.Function(this, 'AnalyticsFunction', {
      functionName: 'shipment-supply-chain-analytics',
      runtime: lambda.Runtime.PYTHON_3_12,
      handler: 'analytics.handler',
      code: lambda.Code.fromAsset('../../infra/lambda-analytics'),
      memorySize: 256,
      timeout: cdk.Duration.minutes(1),
      environment: {
        S3_BUCKET: dataLakeBucket.bucketName,
        SENSOR_TABLE: sensorReadingsTable.tableName,
        TRACKING_TABLE: shipmentTrackingTable.tableName,
      },
    });

    sensorReadingsTable.grantReadData(analyticsLambda);
    shipmentTrackingTable.grantReadData(analyticsLambda);
    dataLakeBucket.grantWrite(analyticsLambda);

    new events.Rule(this, 'AnalyticsSchedule', {
      ruleName: 'shipment-analytics-every-15min',
      schedule: events.Schedule.rate(cdk.Duration.minutes(15)),
      targets: [new targets.LambdaFunction(analyticsLambda)],
    });

    const glueDatabase = new glue.CfnDatabase(this, 'LogisticsGlueDatabase', {
      catalogId: this.account,
      databaseInput: {
        name: 'shipment_logistics_dw',
        description: 'Data warehouse for logistics telemetry and document text extraction',
      },
    });

    const crawlerRole = new iam.Role(this, 'GlueCrawlerRole', {
      assumedBy: new iam.ServicePrincipal('glue.amazonaws.com'),
      managedPolicies: [
        iam.ManagedPolicy.fromAwsManagedPolicyName('service-role/AWSGlueServiceRole'),
      ],
    });
    dataLakeBucket.grantRead(crawlerRole);

    new glue.CfnCrawler(this, 'LogisticsDocumentCrawler', {
      name: 'shipment-document-crawler',
      role: crawlerRole.roleArn,
      databaseName: 'shipment_logistics_dw',
      targets: {
        s3Targets: [{ path: `s3://${dataLakeBucket.bucketName}/reports/extracted/` }],
      },
      schemaChangePolicy: {
        updateBehavior: 'UPDATE_IN_DATABASE',
        deleteBehavior: 'DEPRECATE_IN_DATABASE',
      },
    });

    new athena.CfnWorkGroup(this, 'LogisticsAthenaWorkgroup', {
      name: 'LogisticsAnalytics',
      description: 'Workgroup for logistics compliance and dynamic document queries',
      workGroupConfiguration: {
        resultConfiguration: {
          outputLocation: `s3://${dataLakeBucket.bucketName}/athena-results/`,
        },
      },
    });

    new cdk.CfnOutput(this, 'DataLakeBucketName', {
      value: dataLakeBucket.bucketName,
    });

    new cdk.CfnOutput(this, 'SensorReadingsTableName', {
      value: sensorReadingsTable.tableName,
    });

    new cdk.CfnOutput(this, 'ShipmentTrackingTableName', {
      value: shipmentTrackingTable.tableName,
    });

    new cdk.CfnOutput(this, 'WalletTransactionQueueUrl', {
      value: walletTransactionQueue.queueUrl,
    });

    new cdk.CfnOutput(this, 'WalletTransactionDlqUrl', {
      value: walletDlq.queueUrl,
    });

    new cdk.CfnOutput(this, 'AnalyticsLambdaArn', {
      value: analyticsLambda.functionArn,
    });
  }
}
