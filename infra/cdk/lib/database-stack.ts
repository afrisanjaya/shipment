import * as cdk from 'aws-cdk-lib';
import * as ec2 from 'aws-cdk-lib/aws-ec2';
import * as rds from 'aws-cdk-lib/aws-rds';
import * as elasticache from 'aws-cdk-lib/aws-elasticache';
import * as secretsmanager from 'aws-cdk-lib/aws-secretsmanager';
import { Construct } from 'constructs';

export interface DatabaseStackProps extends cdk.StackProps {
  vpc: ec2.Vpc;
}

export class DatabaseStack extends cdk.Stack {
  constructor(scope: Construct, id: string, props: DatabaseStackProps) {
    super(scope, id, props);

    const { vpc } = props;

    const isolatedSubnets = { subnets: vpc.isolatedSubnets };

    const bookingDbSecret = new secretsmanager.Secret(this, 'BookingDbSecret', {
      secretName: 'shipment/booking-db',
      generateSecretString: {
        secretStringTemplate: JSON.stringify({ username: 'booking_user' }),
        generateStringKey: 'password',
        excludePunctuation: true,
      },
    });

    const logisticsDbSecret = new secretsmanager.Secret(this, 'LogisticsDbSecret', {
      secretName: 'shipment/logistics-db',
      generateSecretString: {
        secretStringTemplate: JSON.stringify({ username: 'logistics_user' }),
        generateStringKey: 'password',
        excludePunctuation: true,
      },
    });

    const walletDbSecret = new secretsmanager.Secret(this, 'WalletDbSecret', {
      secretName: 'shipment/wallet-db',
      generateSecretString: {
        secretStringTemplate: JSON.stringify({ username: 'wallet_user' }),
        generateStringKey: 'password',
        excludePunctuation: true,
      },
    });

    const dbSubnetGroup = new rds.SubnetGroup(this, 'RdsSubnetGroup', {
      description: 'Shipment RDS subnet group (isolated subnets)',
      vpc,
      vpcSubnets: isolatedSubnets,
      subnetGroupName: 'shipment-rds-subnet-group',
    });

    const commonRdsProps: Partial<rds.DatabaseInstanceProps> = {
      engine: rds.DatabaseInstanceEngine.postgres({ version: rds.PostgresEngineVersion.VER_16_3 }),
      instanceType: ec2.InstanceType.of(ec2.InstanceClass.T3, ec2.InstanceSize.MICRO),
      vpc,
      vpcSubnets: isolatedSubnets,
      subnetGroup: dbSubnetGroup,
      multiAz: false,
      storageEncrypted: true,
      backupRetention: cdk.Duration.days(7),
      deletionProtection: false,
      removalPolicy: cdk.RemovalPolicy.DESTROY,
    };

    const bookingRds = new rds.DatabaseInstance(this, 'BookingRds', {
      ...commonRdsProps as rds.DatabaseInstanceProps,
      databaseName: 'booking_db',
      credentials: rds.Credentials.fromSecret(bookingDbSecret),
      instanceIdentifier: 'shipment-rds-booking',
    });

    const logisticsRds = new rds.DatabaseInstance(this, 'LogisticsRds', {
      ...commonRdsProps as rds.DatabaseInstanceProps,
      databaseName: 'logistics_db',
      credentials: rds.Credentials.fromSecret(logisticsDbSecret),
      instanceIdentifier: 'shipment-rds-logistics',
    });

    const walletRds = new rds.DatabaseInstance(this, 'WalletRds', {
      ...commonRdsProps as rds.DatabaseInstanceProps,
      databaseName: 'wallet_db',
      credentials: rds.Credentials.fromSecret(walletDbSecret),
      instanceIdentifier: 'shipment-rds-wallet',
    });

    const redisSubnetGroup = new elasticache.CfnSubnetGroup(this, 'RedisSubnetGroup', {
      description: 'Shipment Redis subnet group',
      subnetIds: vpc.isolatedSubnets.map(s => s.subnetId),
      cacheSubnetGroupName: 'shipment-redis-subnet-group',
    });

    new elasticache.CfnReplicationGroup(this, 'RedisCluster', {
      replicationGroupDescription: 'Shipment Redis — Flash Sale & Session Cache',
      replicationGroupId: 'shipment-redis',
      cacheNodeType: 'cache.t3.micro',
      engine: 'redis',
      engineVersion: '7.1',
      numCacheClusters: 1,
      cacheSubnetGroupName: redisSubnetGroup.ref,
      atRestEncryptionEnabled: true,
      transitEncryptionEnabled: true,
      automaticFailoverEnabled: false,
    });

    new cdk.CfnOutput(this, 'BookingRdsEndpoint', { value: bookingRds.dbInstanceEndpointAddress });
    new cdk.CfnOutput(this, 'LogisticsRdsEndpoint', { value: logisticsRds.dbInstanceEndpointAddress });
    new cdk.CfnOutput(this, 'WalletRdsEndpoint', { value: walletRds.dbInstanceEndpointAddress });
  }
}
