import * as cdk from 'aws-cdk-lib';
import * as ec2 from 'aws-cdk-lib/aws-ec2';
import * as iam from 'aws-cdk-lib/aws-iam';
import { Construct } from 'constructs';

export interface ComputeStackProps extends cdk.StackProps {
  vpc: ec2.Vpc;
}

export class ComputeStack extends cdk.Stack {
  constructor(scope: Construct, id: string, props: ComputeStackProps) {
    super(scope, id, props);

    const computeSg = new ec2.SecurityGroup(this, 'ComputeSg', {
      vpc: props.vpc,
      securityGroupName: 'shipment-compute-sg',
      description: 'EC2 compute — SSH + all service ports',
      allowAllOutbound: true,
    });

    computeSg.addIngressRule(ec2.Peer.anyIpv4(), ec2.Port.tcp(22), 'SSH');
    computeSg.addIngressRule(ec2.Peer.anyIpv4(), ec2.Port.tcp(3000), 'Grafana');
    computeSg.addIngressRule(ec2.Peer.anyIpv4(), ec2.Port.tcp(9090), 'Prometheus');
    computeSg.addIngressRule(ec2.Peer.anyIpv4(), ec2.Port.tcpRange(8081, 8085), 'Service ports');

    const ec2Role = new iam.Role(this, 'Ec2Role', {
      assumedBy: new iam.ServicePrincipal('ec2.amazonaws.com'),
      managedPolicies: [
        iam.ManagedPolicy.fromAwsManagedPolicyName('AmazonSSMManagedInstanceCore'),
        iam.ManagedPolicy.fromAwsManagedPolicyName('CloudWatchAgentServerPolicy'),
      ],
    });

    ec2Role.addToPolicy(new iam.PolicyStatement({
      actions: [
        's3:PutObject', 's3:GetObject', 's3:ListBucket',
        'dynamodb:PutItem', 'dynamodb:GetItem', 'dynamodb:Query', 'dynamodb:Scan',
        'sqs:SendMessage', 'sqs:ReceiveMessage', 'sqs:DeleteMessage',
      ],
      resources: ['*'],
    }));

    ec2Role.addToPolicy(new iam.PolicyStatement({
      actions: [
        'bedrock:InvokeAgent',
        'bedrock:RetrieveAndGenerate',
        'bedrock:StartIngestionJob',
        'bedrock:ListDataSources',
      ],
      resources: ['*'],
    }));

    const userData = ec2.UserData.forLinux();
    userData.addCommands(
      '#!/bin/bash',
      'set -e',
      '',
      'dnf update -y',
      '',
      'dnf install -y docker git',
      'systemctl enable docker',
      'systemctl start docker',
      'usermod -aG docker ec2-user',
      '',
      'curl -L "https://github.com/docker/compose/releases/latest/download/docker-compose-$(uname -s)-$(uname -m)" -o /usr/local/bin/docker-compose',
      'chmod +x /usr/local/bin/docker-compose',
      '',
      'cd /home/ec2-user',
      'git clone https://github.com/your-org/shipment.git shipment',
      'cd shipment/infra/docker',
      'cp .env.example .env',
      'docker compose up -d',
      '',
      'echo "Shipment platform started — all services running"',
    );

    const instance = new ec2.Instance(this, 'ComputeInstance', {
      vpc: props.vpc,
      instanceType: ec2.InstanceType.of(ec2.InstanceClass.T3, ec2.InstanceSize.MEDIUM),
      machineImage: ec2.MachineImage.latestAmazonLinux2023(),
      securityGroup: computeSg,
      role: ec2Role,
      vpcSubnets: { subnetType: ec2.SubnetType.PUBLIC },
      associatePublicIpAddress: true,
      requireImdsv2: true,
      blockDevices: [{
        deviceName: '/dev/xvda',
        volume: ec2.BlockDeviceVolume.ebs(30, {
          volumeType: ec2.EbsDeviceVolumeType.GP3,
          encrypted: true,
        }),
      }],
    });

    new cdk.CfnOutput(this, 'InstancePublicDns', {
      value: instance.instancePublicDnsName,
    });

    new cdk.CfnOutput(this, 'InstancePublicIp', {
      value: instance.instancePublicIp,
    });

    new cdk.CfnOutput(this, 'SshCommand', {
      value: `ssh -i shipment-key.pem ec2-user@${instance.instancePublicDnsName}`,
    });

    new cdk.CfnOutput(this, 'ServicesUrl', {
      value: `http://${instance.instancePublicIp}`,
    });
  }
}
