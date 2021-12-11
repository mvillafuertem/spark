package io.github.mvillafuertem.map.reduce

import com.hashicorp.cdktf
import com.hashicorp.cdktf.{AppOptions, TerraformOutput, TerraformStack}
import imports.archive.DataArchiveFile
import imports.aws.AwsProvider
import imports.aws.ec2.{DataAwsAmi, DataAwsAmiFilter, Instance, InstanceNetworkInterface}
import imports.aws.iam._
import imports.aws.lambdafunction.LambdaFunction
import imports.aws.vpc._
import software.constructs.Construct

import scala.jdk.CollectionConverters._

final class ProcessingServerLogsWithHive(scope: Construct, id: String) extends TerraformStack(scope, id) {
  self: Construct =>

  private val accountId             = "582268654997"
  private val region                = "eu-west-3"
  private val sharedCredentialsFile = "~/.aws/credentials"
  private val profile               = "sngular"

  private val _: AwsProvider = AwsProvider.Builder
    .create(self, "cdktf_aws_provider")
    .allowedAccountIds(List(accountId).asJava)
    .region(region)
    .sharedCredentialsFile(sharedCredentialsFile)
    .profile(profile)
    .build()

  private val labVPC: Vpc = Vpc.Builder
    .create(self, "lab_vpc")
    .cidrBlock("10.0.0.0/16")
    .enableDnsSupport(true)
    .enableDnsHostnames(true)
    .tags(
      Map(
        "Name" -> "Lab VPC",
        "VPC"  -> "LAB"
      ).asJava
    )
    .build()

  private val internetGateway: InternetGateway = InternetGateway.Builder
    .create(self, "internet_gateway")
    .vpcId(labVPC.getId)
    .build()

  private val publicSubnet1: Subnet = Subnet.Builder
    .create(self, "public_subnet_1")
    .cidrBlock("10.0.0.0/24")
    .vpcId(labVPC.getId)
    .mapPublicIpOnLaunch(true)
    .tags(
      Map(
        "Name" -> "Public Subnet 1"
      ).asJava
    )
    .build()

  private val publicRouteTable: RouteTable = RouteTable.Builder
    .create(self, "public_route_table")
    .vpcId(labVPC.getId)
    .tags(
      Map(
        "Name" -> "Public"
      ).asJava
    )
    .build()

  private val publicRoute: Route = Route.Builder
    .create(self, "public_route")
    .routeTableId(publicRouteTable.getId)
    .destinationCidrBlock("0.0.0.0/0")
    .gatewayId(internetGateway.getId)
    .build()

  private val publicSubnet1RouteTableAssociation: RouteTableAssociation = RouteTableAssociation.Builder
    .create(self, "public_subnet_1_route_table_association")
    .subnetId(publicSubnet1.getId)
    .routeTableId(publicRouteTable.getId)
    .build()

  private val awsEmrServicePolicy: IamPolicy = IamPolicy.Builder
    .create(self, "aws_emr_service_policy")
    .name("aws-emr-service-policy")
    .policy("""{
              |  "Version": "2012-10-17",
              |  "Statement": [
              |    {
              |      "Effect": "Allow",
              |      "Action": [
              |        "ec2:AuthorizeSecurityGroupEgress",
              |        "ec2:AuthorizeSecurityGroupIngress",
              |        "ec2:CancelSpotInstanceRequests",
              |        "ec2:CreateNetworkInterface",
              |        "ec2:CreateSecurityGroup",
              |        "ec2:CreateTags",
              |        "ec2:DeleteNetworkInterface",
              |        "ec2:DeleteSecurityGroup",
              |        "ec2:DeleteTags",
              |        "ec2:DescribeAvailabilityZones",
              |        "ec2:DescribeAccountAttributes",
              |        "ec2:DescribeDhcpOptions",
              |        "ec2:DescribeInstanceStatus",
              |        "ec2:DescribeInstances",
              |        "ec2:DescribeKeyPairs",
              |        "ec2:DescribeNetworkAcls",
              |        "ec2:DescribeNetworkInterfaces",
              |        "ec2:DescribePrefixLists",
              |        "ec2:DescribeRouteTables",
              |        "ec2:DescribeSecurityGroups",
              |        "ec2:DescribeSpotInstanceRequests",
              |        "ec2:DescribeSpotPriceHistory",
              |        "ec2:DescribeSubnets",
              |        "ec2:DescribeVpcAttribute",
              |        "ec2:DescribeVpcEndpoints",
              |        "ec2:DescribeVpcEndpointServices",
              |        "ec2:DescribeVpcs",
              |        "ec2:DetachNetworkInterface",
              |        "ec2:ModifyImageAttribute",
              |        "ec2:ModifyInstanceAttribute",
              |        "ec2:RequestSpotInstances",
              |        "ec2:RevokeSecurityGroupEgress",
              |        "ec2:RunInstances",
              |        "ec2:TerminateInstances",
              |        "ec2:DeleteVolume",
              |        "ec2:DescribeVolumeStatus",
              |        "ec2:DescribeVolumes",
              |        "ec2:DetachVolume",
              |        "iam:GetRole",
              |        "iam:GetRolePolicy",
              |        "iam:ListInstanceProfiles",
              |        "iam:ListRolePolicies",
              |        "s3:CreateBucket",
              |        "s3:Get*",
              |        "s3:List*",
              |        "sdb:BatchPutAttributes",
              |        "sdb:Select",
              |        "sqs:CreateQueue",
              |        "sqs:Delete*",
              |        "sqs:GetQueue*",
              |        "sqs:PurgeQueue",
              |        "sqs:ReceiveMessage",
              |        "cloudwatch:PutMetricAlarm",
              |        "cloudwatch:DescribeAlarms",
              |        "cloudwatch:DeleteAlarms",
              |        "application-autoscaling:RegisterScalableTarget",
              |        "application-autoscaling:DeregisterScalableTarget",
              |        "application-autoscaling:PutScalingPolicy",
              |        "application-autoscaling:DeleteScalingPolicy",
              |        "application-autoscaling:Describe*"
              |      ],
              |      "Resource": "*"
              |    },
              |    {
              |      "Effect": "Allow",
              |      "Action": "iam:CreateServiceLinkedRole",
              |      "Resource": "arn:aws:iam::*:role/aws-service-role/spot.amazonaws.com/AWSServiceRoleForEC2Spot*",
              |      "Condition": {
              |        "StringLike": {
              |          "iam:AWSServiceName": "spot.amazonaws.com"
              |        }
              |      }
              |    },
              |    {
              |      "Effect": "Allow",
              |      "Action": "iam:PassRole",
              |      "Resource": "arn:aws:iam::*:role/EMR_EC2_DefaultRole"
              |    },
              |    {
              |      "Effect": "Deny",
              |      "Action": "ec2:RunInstances",
              |      "Resource": "arn:aws:ec2:*:*:instance/*",
              |      "Condition": {
              |        "StringNotEquals": {
              |          "ec2:InstanceType": [
              |            "m4.large",
              |            "m5.large",
              |            "m3.xlarge",
              |            "m4.xlarge",
              |            "m5.xlarge"
              |          ]
              |        }
              |      }
              |    },
              |    {
              |      "Effect": "Deny",
              |      "Action": [
              |        "ec2:*Spot*"
              |      ],
              |      "Resource": "*"
              |    }
              |  ]
              |}""".stripMargin)
    .build()

  private val emrRole: IamRole = IamRole.Builder
    .create(self, "emr_role")
    .name("emr_role")
    .assumeRolePolicy("""{
                        |  "Version": "2012-10-17",
                        |  "Statement": [
                        |    {
                        |      "Effect": "Allow",
                        |      "Principal": {
                        |        "Service": "elasticmapreduce.amazonaws.com"
                        |      },
                        |      "Action": "sts:AssumeRole"
                        |    }
                        |  ]
                        |}""".stripMargin)
    .maxSessionDuration(3600)
    .path("/")
    .build()

  private val _: IamRolePolicyAttachment = IamRolePolicyAttachment.Builder
    .create(self, "iam_emr_role_policy_attachment")
    .role(emrRole.getName)
    .policyArn(awsEmrServicePolicy.getArn)
    .build()

  private val ec2Role: IamRole = IamRole.Builder
    .create(self, "ec2_role")
    .name("ec2_role")
    .assumeRolePolicy("""{
                        |  "Version": "2012-10-17",
                        |  "Statement": [
                        |    {
                        |      "Effect": "Allow",
                        |      "Principal": {
                        |        "Service": "ec2.amazonaws.com"
                        |      },
                        |      "Action": "sts:AssumeRole"
                        |    }
                        |  ]
                        |}""".stripMargin)
    .maxSessionDuration(3600)
    .path("/")
    .build()

  private val awsEc2ServicePolicy: IamPolicy = IamPolicy.Builder
    .create(self, "aws_ec2_service_policy")
    .name("aws-ec2-service-policy")
    .policy("""{
              |  "Version": "2012-10-17",
              |  "Statement": [
              |    {
              |      "Effect": "Allow",
              |      "Action": [
              |        "cloudwatch:*",
              |        "dynamodb:*",
              |        "ec2:Describe*",
              |        "elasticmapreduce:Describe*",
              |        "elasticmapreduce:ListBootstrapActions",
              |        "elasticmapreduce:ListClusters",
              |        "elasticmapreduce:ListInstanceGroups",
              |        "elasticmapreduce:ListInstances",
              |        "elasticmapreduce:ListSteps",
              |        "kinesis:CreateStream",
              |        "kinesis:DeleteStream",
              |        "kinesis:DescribeStream",
              |        "kinesis:GetRecords",
              |        "kinesis:GetShardIterator",
              |        "kinesis:MergeShards",
              |        "kinesis:PutRecord",
              |        "kinesis:SplitShard",
              |        "rds:Describe*",
              |        "s3:*",
              |        "sdb:*",
              |        "sns:*",
              |        "sqs:*",
              |        "kms:*",
              |        "glue:CreateDatabase",
              |        "glue:UpdateDatabase",
              |        "glue:DeleteDatabase",
              |        "glue:GetDatabase",
              |        "glue:GetDatabases",
              |        "glue:CreateTable",
              |        "glue:UpdateTable",
              |        "glue:DeleteTable",
              |        "glue:GetTable",
              |        "glue:GetTables",
              |        "glue:GetTableVersions",
              |        "glue:CreatePartition",
              |        "glue:BatchCreatePartition",
              |        "glue:UpdatePartition",
              |        "glue:DeletePartition",
              |        "glue:BatchDeletePartition",
              |        "glue:GetPartition",
              |        "glue:GetPartitions",
              |        "glue:BatchGetPartition",
              |        "glue:CreateUserDefinedFunction",
              |        "glue:UpdateUserDefinedFunction",
              |        "glue:DeleteUserDefinedFunction",
              |        "glue:GetUserDefinedFunction",
              |        "glue:GetUserDefinedFunctions"
              |      ],
              |      "Resource": "*"
              |    }
              |  ]
              |}""".stripMargin)
    .build()

  private val _: IamRolePolicyAttachment = IamRolePolicyAttachment.Builder
    .create(self, "iam_ec2_role_policy_attachment")
    .role(ec2Role.getName)
    .policyArn(awsEc2ServicePolicy.getArn)
    .build()

  private val createInstanceProfileRole: IamRole = IamRole.Builder
    .create(self, "create_instance_profile_role")
    .name("create_instance_profile_role")
    .assumeRolePolicy("""{
                        |  "Version": "2012-10-17",
                        |  "Statement": [
                        |    {
                        |      "Effect": "Allow",
                        |      "Principal": {
                        |        "Service": "lambda.amazonaws.com"
                        |      },
                        |      "Action": "sts:AssumeRole"
                        |    }
                        |  ]
                        |}""".stripMargin)
    .maxSessionDuration(3600)
    .path("/")
    .build()

  private val createInstanceProfilePolicy: IamPolicy = IamPolicy.Builder
    .create(self, "create_instance_profile_policy")
    .name("create-instance-profile-policy")
    .policy("""{
              |  "Version": "2012-10-17",
              |  "Statement": [
              |    {
              |      "Effect": "Allow",
              |      "Action": [
              |        "logs:CreateLogGroup",
              |        "logs:CreateLogStream",
              |        "logs:PutLogEvents"
              |      ],
              |      "Resource": "arn:aws:logs:*:*:*"
              |    },
              |    {
              |      "Effect": "Allow",
              |      "Action": [
              |        "iam:CreateInstanceProfile",
              |        "iam:DeleteInstanceProfile",
              |        "iam:AddRoleToInstanceProfile"
              |      ],
              |      "Resource": "*"
              |    },
              |    {
              |      "Effect": "Allow",
              |      "Action": [
              |        "iam:PassRole"
              |      ],
              |      "Resource": "arn:aws:iam::*:role/EMR_EC2_DefaultRole"
              |    }
              |  ]
              |}""".stripMargin)
    .build()

  imports.archive.ArchiveProvider.Builder.create(self, "archive_provider").build()

  private val _: IamRolePolicyAttachment = IamRolePolicyAttachment.Builder
    .create(self, "iam_instance_role_policy_attachment")
    .role(createInstanceProfileRole.getName)
    .policyArn(createInstanceProfilePolicy.getArn)
    .build()

  private val createInstanceProfileFunctionArchiveFile: DataArchiveFile = DataArchiveFile.Builder
    .create(self, "create_instance_profile_function_archive_file")
    .`type`("zip")
    .sourceFile("/Users/mvillafuerte/Projects/spark/modules/cdktf/src/main/scala/io/github/mvillafuertem/spark/cdktf/emr/index.js")
    .outputFileMode("0666")
    .outputPath("/Users/mvillafuerte/Projects/spark/modules/cdktf/target/createInstanceProfileFunction.zip")
    .build()

  private val _: LambdaFunction = LambdaFunction.Builder
    .create(self, "create_instance_profile_function")
    .filename(createInstanceProfileFunctionArchiveFile.getOutputPath)
    .sourceCodeHash(createInstanceProfileFunctionArchiveFile.getOutputBase64Sha256)
    .functionName("CreateInstanceProfileFunction")
    .memorySize(128)
    .runtime("nodejs12.x")
    .timeout(90)
    .role(createInstanceProfileRole.getId)
    .handler("index.handler")
    .build()

  private val lambdaVPCRole: IamRole = IamRole.Builder
    .create(self, "lambda_vpc_role")
    .name("lambda_vpc_role")
    .assumeRolePolicy("""{
                        |  "Version": "2012-10-17",
                        |  "Statement": [
                        |    {
                        |      "Effect": "Allow",
                        |      "Principal": {
                        |        "Service": "lambda.amazonaws.com"
                        |      },
                        |      "Action": "sts:AssumeRole"
                        |    }
                        |  ]
                        |}""".stripMargin)
    .maxSessionDuration(3600)
    .path("/")
    .inlinePolicy(
      List(
        IamRoleInlinePolicy
          .builder()
          .name("LambdaLogToCloudWatch")
          .policy("""{
                    |  "Version": "2012-10-17",
                    |  "Statement": [
                    |    {
                    |      "Effect": "Allow",
                    |      "Action": [
                    |        "logs:CreateLogGroup",
                    |        "logs:CreateLogStream",
                    |        "logs:PutLogEvents"
                    |      ],
                    |      "Resource": "arn:aws:logs:*:*:*"
                    |    }
                    |  ]
                    |}""".stripMargin)
          .build(),
        IamRoleInlinePolicy
          .builder()
          .name("Lambda_VPC_Policy")
          .policy("""{
                    |  "Version": "2012-10-17",
                    |  "Statement": [
                    |    {
                    |      "Effect": "Allow",
                    |      "Action": [
                    |        "ec2:CreateDefaultVpc",
                    |        "ec2:DescribeVpcs"
                    |      ],
                    |      "Resource": "*"
                    |    }
                    |  ]
                    |}""".stripMargin)
          .build()
      ).asJava
    )
    .build()

  private val lambdaCreateDefaultVPCArchiveFile: DataArchiveFile = DataArchiveFile.Builder
    .create(self, "lambda_create_default_vpc_archive_file")
    .`type`("zip")
    .sourceFile("/Users/mvillafuerte/Projects/spark/modules/cdktf/src/main/scala/io/github/mvillafuertem/spark/cdktf/emr/index.py")
    .outputFileMode("0666")
    .outputPath("/Users/mvillafuerte/Projects/spark/modules/cdktf/target/lambdaCreateDefaultVPC.zip")
    .build()

  val lambdaCreateDefaultVPC: LambdaFunction = LambdaFunction.Builder
    .create(self, "lambda_create_default_vpc")
    .functionName("LambdaCreateDefaultVPC")
    .runtime("python3.8")
    .filename(lambdaCreateDefaultVPCArchiveFile.getOutputPath)
    .sourceCodeHash(lambdaCreateDefaultVPCArchiveFile.getOutputBase64Sha256)
    .memorySize(512)
    .timeout(120)
    .role(lambdaVPCRole.getArn)
    .handler("index.lambda_handler")
    .build()

  private val commandHostSecurityGroup: SecurityGroup = SecurityGroup.Builder
    .create(self, "command_host_security_group")
    .name("command_host_security_group")
    .ingress(
      List(
        SecurityGroupIngress
          .builder()
          .fromPort(22)
          .toPort(22)
          .protocol("tcp")
          .cidrBlocks(List("10.0.0.0/16").asJava)
          .build()
      ).asJava
    )
    .build()

  private val commandHostRole: IamRole = IamRole.Builder
    .create(self, "command_host_role")
    .name("command_host_role")
    .managedPolicyArns(List("arn:aws:iam::aws:policy/service-role/AmazonEC2RoleforSSM").asJava)
    .assumeRolePolicy("""{
                        |  "Version": "2012-10-17",
                        |  "Statement": [
                        |    {
                        |      "Effect": "Allow",
                        |      "Principal": {
                        |        "Service": "ec2.amazonaws.com"
                        |      },
                        |      "Action": "sts:AssumeRole"
                        |    }
                        |  ]
                        |}""".stripMargin)
    .inlinePolicy(
      List(
        IamRoleInlinePolicy
          .builder()
          .name("root")
          .policy(s"""{
                     |  "Version": "2012-10-17",
                     |  "Statement": [
                     |    {
                     |      "Effect": "Allow",
                     |      "Action": [
                     |        "ec2:CreateKeyPair",
                     |        "ec2:DeleteKeyPair"
                     |      ],
                     |      "Resource": "arn:aws:ec2:${region}:${accountId}:key-pair/EMRKey"
                     |    },
                     |    {
                     |      "Effect": "Allow",
                     |      "Action": [
                     |        "elasticmapreduce:List*",
                     |        "elasticmapreduce:Describe*"
                     |      ],
                     |      "Resource": "*"
                     |    }
                     |  ]
                     |}""".stripMargin)
          .build()
      ).asJava
    )
    .build()

  private val commandHostInstanceProfile: IamInstanceProfile = IamInstanceProfile.Builder
    .create(self, "command_host_instance_profile")
    .name("command_host_instance_profile")
    .role(commandHostRole.getName)
    .build()
  private val networkInterface: NetworkInterface             = NetworkInterface.Builder
    .create(self, "network_interface")
    .subnetId(publicSubnet1.getId)
    .securityGroups(List(commandHostSecurityGroup.getName).asJava)
    .build()

  private val dataAwsAmi: DataAwsAmi = DataAwsAmi.Builder
    .create(self, "amazon_linux_ami_id")
    .mostRecent(true)
    .owners(List("amazon").asJava)
    .filter(
      List(
        DataAwsAmiFilter
          .builder()
          .name("name")
          .values(List("amzn-ami-hvm*").asJava)
          .build()
      ).asJava
    )
    .build()

  private val commandHost: Instance = Instance.Builder
    .create(self, "command_host_instance")
    .ami(dataAwsAmi.getId)
    .iamInstanceProfile(commandHostInstanceProfile.getName)
    .instanceType("t3.micro")
    .networkInterface(
      List(
        InstanceNetworkInterface
          .builder()
          .deviceIndex(0)
          .networkInterfaceId(networkInterface.getId)
          .build()
      ).asJava
    )
    .tags(Map("Name" -> "CommandHost").asJava)
    .userData(s"""#!/bin/bash -ex
                 |# Installing updates and jq
                 |yum update -y
                 |yum -y install jq
                 |
                 |## Deleting existing EMRKey pair if it exists and creating new one.
                 |if ($$(aws ec2 create-key-pair --key-name EMRKey --region ${region} --query 'KeyMaterial' --output text > /home/ec2-user/EMRKey.pem)); then
                 |  echo "Good"
                 |elif ($$(aws ec2 delete-key-pair --key-name EMRKey --region ${region})); then
                 |    echo "Deleted"
                 |    aws ec2 create-key-pair --key-name EMRKey --region ${region} --query 'KeyMaterial' --output text > /home/ec2-user/EMRKey.pem
                 |    echo "Redone the key"
                 |fi
                 |
                 |# Changing the key file to read permission
                 |chmod +r /home/ec2-user/EMRKey.pem &&
                 |
                 |# Checking if ssm-user exist in the EC2 instance. If exists, the condition will pass else will create ssm-user
                 |if id -u "ssm-user" >/dev/null 2>&1; then
                 |  echo 'ssm-user already exists'
                 |else
                 |  useradd ssm-user -m -U
                 |fi
                 |
                 |# Updating aws configuration for ssm-user
                 |mkdir /home/ssm-user/.aws &&
                 |echo "[default]" > /home/ssm-user/.aws/config &&
                 |echo "region = ${region}" >> /home/ssm-user/.aws/config &&
                 |cp /home/ec2-user/EMRKey.pem /home/ssm-user/ &&
                 |chmod +r /home/ssm-user/EMRKey.pem
                 |
                 |# Starting wait condition - this delay is introduced to make sure emr cluster gets the correct key pair.
                 |""".stripMargin)
    .build()

  private val readOnlyGroup: IamGroup = IamGroup.Builder
    .create(self, "read_only_group")
    .name("ReadOnlyGroup")
    .build()

  private val iamGroupPolicyAttachment: IamGroupPolicyAttachment = IamGroupPolicyAttachment.Builder
    .create(self, "iam_group_policy_attachment")
    .policyArn("arn:aws:iam::aws:policy/ReadOnlyAccess")
    .group(readOnlyGroup.getName)
    .build()

  private val _: TerraformOutput = TerraformOutput.Builder
    .create(self, "lab_region_terraform_output")
    .description("Lab Region")
    .value(region)
    .build()

  private val _: TerraformOutput = TerraformOutput.Builder
    .create(self, "command_host_session_management_url")
    .description("The URL to the Session Management Console for CommandHost")
    .value(s"https://${region}.console.aws.amazon.com/systems-manager/session-manager/${commandHost}?region=${region}")
    .build()

  private val _: TerraformOutput = TerraformOutput.Builder
    .create(self, "command_host_ip")
    .description("CommandHost Public IP")
    .value(commandHost.getPublicIp)
    .build()
  //  private val s3Bucket: S3Bucket = S3Bucket.Builder
//    .create(self, s"hive_bucket_$accountId")
//    .bucket(s"hive-bucket-$accountId")
//    .forceDestroy(true)
//    .build()
//
//  EmrCluster.Builder
//    .create()
//    .releaseLabel("emr-5.29.0")
//    .applications(List("Hadoop", "Hive").asJava)
//    .ec2Attributes(EmrClusterEc2Attributes.builder())
}

// saml2aws login
// sbt "map-reduce/runMain io.github.mvillafuertem.map.reduce.ProcessingServerLogsWithHive"
// yarn --cwd modules/map-reduce/ planProcessingServerLogsWithHive
// yarn --cwd modules/map-reduce/ applyProcessingServerLogsWithHive
// yarn --cwd modules/map-reduce/ destroyProcessingServerLogsWithHive
object ProcessingServerLogsWithHive extends App {

  private val app: cdktf.App = new cdktf.App(
    AppOptions
      .builder()
      .stackTraces(false)
      .outdir("modules/cdktf/src/main/resources/")
      .context(
        Map(
          "excludeStackIdFromLogicalIds" -> true,
          "allowSepCharsInLogicalIds"    -> true
        ).asJava
      )
      .build()
  )

  new ProcessingServerLogsWithHive(app, "processing-server-logs-with-hive")
  app.synth()

}
