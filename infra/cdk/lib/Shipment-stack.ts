import * as cdk from 'aws-cdk-lib';
import { Construct } from 'constructs';
import { NetworkingStack } from './networking-stack';
import { DatabaseStack } from './database-stack';
import { BedrockStack } from './bedrock-stack';
import { AwsResourcesStack } from './aws-resources-stack';
import { ComputeStack } from './compute-stack';

const app = new cdk.App();

const env: cdk.Environment = {
  account: process.env.CDK_DEFAULT_ACCOUNT,
  region: process.env.CDK_DEFAULT_REGION,
};

const networkingStack = new NetworkingStack(app, 'ShipmentNetworkingStack', {
  env,
  description: 'Shipment — VPC, Subnets, Security Groups',
});

const databaseStack = new DatabaseStack(app, 'ShipmentDatabaseStack', {
  env,
  vpc: networkingStack.vpc,
  description: 'Shipment — RDS PostgreSQL, ElastiCache Redis',
});
databaseStack.addDependency(networkingStack);

const bedrockStack = new BedrockStack(app, 'ShipmentBedrockStack', {
  env,
  description: 'Shipment — Bedrock Agent, Knowledge Base, S3 Vectors (PLACEHOLDER)',
});
bedrockStack.addDependency(networkingStack);

const awsResourcesStack = new AwsResourcesStack(app, 'AwsResourcesStack', {
  env,
  description: 'Shipment — S3 Data Lake, DynamoDB Time-Series, SQS FIFO (Free Tier)',
});

const computeStack = new ComputeStack(app, 'ShipmentComputeStack', {
  env,
  vpc: networkingStack.vpc,
  description: 'Shipment — EC2 Compute (t3.medium) — cold-start reference only',
});
computeStack.addDependency(networkingStack);
computeStack.addDependency(databaseStack);

app.synth();
