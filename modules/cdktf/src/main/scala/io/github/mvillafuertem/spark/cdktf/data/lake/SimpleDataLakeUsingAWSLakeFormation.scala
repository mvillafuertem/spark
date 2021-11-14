package io.github.mvillafuertem.spark.cdktf.data.lake

import com.hashicorp.cdktf
import com.hashicorp.cdktf.{AppOptions, TerraformStack}
import imports.aws.AwsProvider
import imports.aws.athena.{AthenaNamedQuery, AthenaWorkgroup, AthenaWorkgroupConfiguration, AthenaWorkgroupConfigurationResultConfiguration}
import imports.aws.glue.{GlueCatalogDatabase, GlueCrawler, GlueCrawlerS3Target}
import imports.aws.iam.{IamPolicy, IamRole, IamRolePolicyAttachment}
import imports.aws.lake_formation._
import imports.aws.s3.{S3Bucket, S3BucketObject}
import software.constructs.Construct

import java.io.File
import java.util.UUID
import scala.jdk.CollectionConverters._

class SimpleDataLakeUsingAWSLakeFormation(scope: Construct, id: String) extends TerraformStack(scope, id) {
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

  private val s3Bucket: S3Bucket = S3Bucket.Builder
    .create(self, "simple_data_lake_using_aws_lake_formation_bucket")
    .bucket("simple-data-lake-using-aws-lake-formation-bucket")
    .forceDestroy(true)
    .build()

  private val s3BucketObject: S3BucketObject = S3BucketObject.Builder
    .create(self, "object_data_bucket")
    .bucket(s3Bucket.getId)
    .key("data/movies.csv")
    .source(new File("modules/cdktf/src/main/resources/data/lake/databucket/data/movies.csv").getAbsolutePath)
    .etag(UUID.randomUUID().toString)
    .build()

  private val iamLakeFormationRole: IamRole = IamRole.Builder
    .create(self, "iam_lake_formation_role")
    .name("iam-lake-formation-role")
    .assumeRolePolicy(s"""{
                         |  "Version": "2012-10-17",
                         |  "Statement": [
                         |    {
                         |      "Effect": "Allow",
                         |      "Principal": {
                         |        "Service": "lakeformation.amazonaws.com"
                         |      },
                         |      "Action": "sts:AssumeRole"
                         |    }
                         |  ]
                         |}""".stripMargin)
    .description("Allows Lake Formation to call AWS Services on your behalf.")
    .maxSessionDuration(3600)
    .build()

  private val iamLakeFormationPolicy: IamPolicy = IamPolicy.Builder
    .create(self, "iam_lake_formation_role_policy")
    .name("iam-lake-formation-role-policy")
    .policy(s"""{
               |  "Version": "2012-10-17",
               |  "Statement": [
               |    {
               |      "Effect": "Allow",
               |      "Action": [
               |        "s3:GetBucketLocation",
               |        "s3:ListAllMyBuckets"
               |      ],
               |      "Resource": "*"
               |    },
               |    {
               |      "Effect": "Allow",
               |      "Action": ["s3:ListBucket"],
               |      "Resource": ["${s3Bucket.getArn}"]
               |    },
               |    {
               |      "Effect": "Allow",
               |      "Action": [
               |        "s3:PutObject",
               |        "s3:GetObject",
               |        "s3:DeleteObject"
               |      ],
               |      "Resource": ["${s3Bucket.getArn}/*"]
               |    },
               |    {
               |      "Effect": "Allow",
               |      "Action": [
               |        "lakeformation:*"
               |      ],
               |      "Resource": "*"
               |    },
               |    {
               |      "Effect": "Allow",
               |      "Action": [
               |        "glue:GetDatabase",
               |        "glue:CreateDatabase",
               |        "glue:GetTable",
               |        "glue:CreateTable"
               |      ],
               |      "Resource": "*"
               |    },
               |    {
               |      "Effect": "Allow",
               |      "Action": [
               |        "logs:CreateLogGroup",
               |        "logs:CreateLogStream",
               |        "logs:PutLogEvents",
               |        "logs:AssociateKmsKey"
               |      ],
               |      "Resource": [
               |        "arn:aws:logs:*:*:/aws-glue/*"
               |      ]
               |    }
               |  ]
               |}""".stripMargin)
    .build()

  private val _: IamRolePolicyAttachment = IamRolePolicyAttachment.Builder
    .create(self, "iam_lake_formation_role_policy_attachment")
    .role(iamLakeFormationRole.getId)
    .policyArn(iamLakeFormationPolicy.getArn)
    .build()

  private val _: LakeformationResource = LakeformationResource.Builder
    .create(self, "lake_formation")
    .arn(s3Bucket.getArn)
    .roleArn(iamLakeFormationRole.getArn)
    .build()

  private val glueCatalogDatabase: GlueCatalogDatabase = GlueCatalogDatabase.Builder
    .create(self, "glue_catalog_database")
    .name("movies-db")
    .locationUri(s"s3://${s3Bucket.getBucket}/data")
    .build()

  private val iamGlueRole: IamRole = IamRole.Builder
    .create(self, "iam_glue_role")
    .name("iam-glue-role")
    .assumeRolePolicy(s"""{
                         |  "Version": "2012-10-17",
                         |  "Statement": [
                         |    {
                         |      "Effect": "Allow",
                         |      "Principal": {
                         |        "Service": "glue.amazonaws.com"
                         |      },
                         |      "Action": "sts:AssumeRole"
                         |    }
                         |  ]
                         |}""".stripMargin)
    .description("Allows Glue to call AWS services on your behalf.")
    .maxSessionDuration(3600)
    .build()

  private val iamGluePolicy: IamPolicy = IamPolicy.Builder
    .create(self, "iam_glue_role_policy")
    .name("iam-glue-role-policy")
    .policy(s"""{
               |  "Version": "2012-10-17",
               |  "Statement": [
               |    {
               |      "Effect": "Allow",
               |      "Action": [
               |        "s3:GetBucketLocation",
               |        "s3:ListAllMyBuckets"
               |      ],
               |      "Resource": "*"
               |    },
               |    {
               |      "Effect": "Allow",
               |      "Action": ["s3:ListBucket"],
               |      "Resource": ["${s3Bucket.getArn}"]
               |    },
               |    {
               |      "Effect": "Allow",
               |      "Action": [
               |        "s3:PutObject",
               |        "s3:GetObject",
               |        "s3:DeleteObject"
               |      ],
               |      "Resource": ["${s3Bucket.getArn}/*"]
               |    },
               |    {
               |      "Effect": "Allow",
               |      "Action": [
               |        "lakeformation:*"
               |      ],
               |      "Resource": "*"
               |    },
               |    {
               |      "Effect": "Allow",
               |      "Action": [
               |        "glue:GetDatabase",
               |        "glue:CreateDatabase",
               |        "glue:GetTable",
               |        "glue:CreateTable"
               |      ],
               |      "Resource": "*"
               |    },
               |    {
               |      "Effect": "Allow",
               |      "Action": [
               |        "logs:CreateLogGroup",
               |        "logs:CreateLogStream",
               |        "logs:PutLogEvents",
               |        "logs:AssociateKmsKey"
               |      ],
               |      "Resource": [
               |        "arn:aws:logs:*:*:/aws-glue/*"
               |      ]
               |    }
               |  ]
               |}""".stripMargin)
    .build()

  private val _: IamRolePolicyAttachment = IamRolePolicyAttachment.Builder
    .create(self, "iam_glue_role_policy_attachment")
    .role(iamGlueRole.getId)
    .policyArn(iamGluePolicy.getArn)
    .build()

  private val glueCrawlerS3Target: GlueCrawlerS3Target = GlueCrawlerS3Target
    .builder()
    .path(s"${s3Bucket.getId}/data")
    .build()

  private val _: GlueCrawler = GlueCrawler.Builder
    .create(self, "glue_crawler")
    .name("movies-tb")
    .s3Target(List(glueCrawlerS3Target).asJava)
    .role(iamGlueRole.getId)
    .databaseName(glueCatalogDatabase.getName)
    .build()

  private val athenaWorkgroupConfigurationResultConfiguration: AthenaWorkgroupConfigurationResultConfiguration = imports.aws.athena.AthenaWorkgroupConfigurationResultConfiguration
    .builder()
    .outputLocation(s"s3://${s3Bucket.getBucket}/results")
    .build()

  private val athenaWorkgroup: AthenaWorkgroup = imports.aws.athena.AthenaWorkgroup.Builder
    .create(self, "athena_workgroup")
    .name("lab1-athena-workgroup")
    .configuration(AthenaWorkgroupConfiguration.builder()
      .resultConfiguration(athenaWorkgroupConfigurationResultConfiguration)
      .build()
    )
    .forceDestroy(true)
    .build()

  private val _: AthenaNamedQuery = AthenaNamedQuery.Builder
    .create(self, "lab1_athena_named_query")
    .name("lab1-athena-named-query")
    .query("SELECT * FROM \"lab1-db\".\"movies_data\" limit 10;")
    .database("lab1-db")
    .workgroup(athenaWorkgroup.getId)
    .build()

  private val _: LakeformationPermissions = LakeformationPermissions.Builder
    .create(self, "lake_formation_permissions")
    .principal(iamLakeFormationRole.getArn)
    .permissions(Seq("DATA_LOCATION_ACCESS").asJava)
    .dataLocation(
      LakeformationPermissionsDataLocation
        .builder()
        .arn(s3Bucket.getArn)
        .build()
    )
    .build()

  private val _: LakeformationDataLakeSettings = LakeformationDataLakeSettings.Builder
    .create(self, "lake_formation_data_lake_settings")
    //.admins(List("").asJava)
    .createDatabaseDefaultPermissions(
      List(
        LakeformationDataLakeSettingsCreateDatabaseDefaultPermissions
          .builder()
          .principal("IAM_ALLOWED_PRINCIPALS")
          .permissions(Seq("ALL").asJava)
          .build()
      ).asJava
    )
    .createTableDefaultPermissions(
      List(
        LakeformationDataLakeSettingsCreateTableDefaultPermissions
          .builder()
          .principal("IAM_ALLOWED_PRINCIPALS")
          .permissions(Seq("ALL").asJava)
          .build()
      ).asJava
    )
    .build()

  imports.aws.glue.GlueJob.Builder
    .create(self, "")
    .name("csv_to_parquet")
    .roleArn(iamGlueRole.getArn)
    .command(imports.aws.glue.GlueJobCommand.builder()
    .scriptLocation("")
      .build())
    .defaultArguments(Map("--job-language" -> "scala").asJava)
    .build()

//  resource "aws_cloudwatch_log_group" "example" {
//    name              = "example"
//    retention_in_days = 14
//  }
//
//  resource "aws_glue_job" "example" {
//    # ... other configuration ...
//
//    default_arguments = {
//      # ... potentially other arguments ...
//      "--continuous-log-logGroup"          = aws_cloudwatch_log_group.example.name
//      "--enable-continuous-cloudwatch-log" = "true"
//      "--enable-continuous-log-filter"     = "true"
//      "--enable-metrics"                   = ""
//    }
//  }

}

// saml2aws login
// sbt "cdktf/runMain io.github.mvillafuertem.spark.cdktf.data.lake.SimpleDataLakeUsingAWSLakeFormation"
// yarn --cwd modules/cdktf/ planLakeFormation
// yarn --cwd modules/cdktf/ applyLakeFormation
// yarn --cwd modules/cdktf/ destroyLakeFormation
object SimpleDataLakeUsingAWSLakeFormation extends App {

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

  new SimpleDataLakeUsingAWSLakeFormation(app, "simple-data-lake-using-aws-lake-formation")
  app.synth()

}
