import * as cdk from 'aws-cdk-lib';
import * as ec2 from 'aws-cdk-lib/aws-ec2';
import { Construct } from 'constructs';

export interface NetworkingStackProps extends cdk.StackProps {}

export class NetworkingStack extends cdk.Stack {
  public readonly vpc: ec2.Vpc;
  public readonly bookingServiceSg: ec2.SecurityGroup;
  public readonly logisticsServiceSg: ec2.SecurityGroup;
  public readonly walletServiceSg: ec2.SecurityGroup;
  public readonly rdsSg: ec2.SecurityGroup;
  public readonly redisSg: ec2.SecurityGroup;

  constructor(scope: Construct, id: string, props?: NetworkingStackProps) {
    super(scope, id, props);

    this.vpc = new ec2.Vpc(this, 'TerApelVpc', {
      vpcName: 'shipment-vpc',
      maxAzs: 2,
      natGateways: 1,
      subnetConfiguration: [
        {
          name: 'Public',
          subnetType: ec2.SubnetType.PUBLIC,
          cidrMask: 24,
        },
        {
          name: 'Private',
          subnetType: ec2.SubnetType.PRIVATE_WITH_EGRESS,
          cidrMask: 24,
        },
        {
          name: 'Isolated',
          subnetType: ec2.SubnetType.PRIVATE_ISOLATED,
          cidrMask: 28,
        },
      ],
    });

    this.rdsSg = new ec2.SecurityGroup(this, 'RdsSg', {
      vpc: this.vpc,
      securityGroupName: 'shipment-rds-sg',
      description: 'Allow PostgreSQL from private subnets only',
    });

    this.redisSg = new ec2.SecurityGroup(this, 'RedisSg', {
      vpc: this.vpc,
      securityGroupName: 'shipment-redis-sg',
      description: 'Allow Redis from private subnets only',
    });

    this.bookingServiceSg = new ec2.SecurityGroup(this, 'BookingServiceSg', {
      vpc: this.vpc,
      securityGroupName: 'shipment-booking-service-sg',
      description: 'Booking Service ECS tasks',
      allowAllOutbound: true,
    });

    this.logisticsServiceSg = new ec2.SecurityGroup(this, 'LogisticsServiceSg', {
      vpc: this.vpc,
      securityGroupName: 'shipment-logistics-service-sg',
      description: 'Logistics Service ECS tasks',
      allowAllOutbound: true,
    });

    this.walletServiceSg = new ec2.SecurityGroup(this, 'WalletServiceSg', {
      vpc: this.vpc,
      securityGroupName: 'shipment-wallet-service-sg',
      description: 'Wallet Service ECS tasks',
      allowAllOutbound: true,
    });

    this.rdsSg.addIngressRule(this.bookingServiceSg, ec2.Port.tcp(5432), 'booking-service → RDS');
    this.rdsSg.addIngressRule(this.logisticsServiceSg, ec2.Port.tcp(5432), 'logistics-service → RDS');
    this.rdsSg.addIngressRule(this.walletServiceSg, ec2.Port.tcp(5432), 'wallet-service → RDS');
    this.redisSg.addIngressRule(this.logisticsServiceSg, ec2.Port.tcp(6379), 'logistics-service → Redis');

    new cdk.CfnOutput(this, 'VpcId', { value: this.vpc.vpcId });
  }
}
