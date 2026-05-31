import * as cdk from 'aws-cdk-lib';
import * as s3 from 'aws-cdk-lib/aws-s3';
import { Construct } from 'constructs';

export class BedrockStack extends cdk.Stack {
  constructor(scope: Construct, id: string, props?: cdk.StackProps) {
    super(scope, id, props);

    const kbBucket = new s3.Bucket(this, 'KnowledgeBaseBucket', {
      bucketName: `shipment-kb-${this.account}`,
      versioned: true,
      encryption: s3.BucketEncryption.S3_MANAGED,
      blockPublicAccess: s3.BlockPublicAccess.BLOCK_ALL,
      removalPolicy: cdk.RemovalPolicy.RETAIN,
    });

    const bedrockAgentArn = '';
    const bedrockAgentAliasId = '';
    const knowledgeBaseId = '';
    const s3VectorBucketArn = '';

    new cdk.CfnOutput(this, 'KnowledgeBaseBucketName', {
      value: kbBucket.bucketName,
    });

    new cdk.CfnOutput(this, 'BedrockAgentArnPlaceholder', {
      value: bedrockAgentArn || 'NOT_SET — fill in BedrockStack.ts',
    });

    new cdk.CfnOutput(this, 'KnowledgeBaseIdPlaceholder', {
      value: knowledgeBaseId || 'NOT_SET — fill in BedrockStack.ts',
    });
  }
}
