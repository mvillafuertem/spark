package io.github.mvillafuertem.spark.cdktf.data.lake

import com.hashicorp.cdktf.TerraformStack
import imports.aws.AwsProvider
import imports.aws.iam.{IamInstanceProfile, IamPolicy, IamRole, IamRolePolicyAttachment}
import imports.aws.vpc.{InternetGateway, Route, RouteTable, RouteTableAssociation, Subnet, Vpc}
import software.constructs.Construct

import scala.jdk.CollectionConverters._

class ProcessingServerLogsWithHiveOnAmazonEMR(scope: Construct, id: String) extends TerraformStack(scope, id) {
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
        "VPC" -> "LAB"
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
    .policy(
      """{
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
    .assumeRolePolicy(
      """{
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
    .role(emrRole.getId)
    .policyArn(awsEmrServicePolicy.getArn)
    .build()

  private val ec2Role: IamRole = IamRole.Builder
    .create(self, "ec2_role")
    .assumeRolePolicy(
      """{
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
    .policy(
      """{
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
    .create(self, "iam_emr_role_policy_attachment")
    .role(ec2Role.getId)
    .policyArn(awsEc2ServicePolicy.getArn)
    .build()

  IamInstanceProfile



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
