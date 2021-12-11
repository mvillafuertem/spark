package io.github.mvillafuertem.data.lake

import com.hashicorp.cdktf
import com.hashicorp.cdktf.{AppOptions, TerraformStack}
import imports.aws.AwsProvider
import imports.aws.athena.{AthenaNamedQuery, AthenaWorkgroup, AthenaWorkgroupConfiguration, AthenaWorkgroupConfigurationResultConfiguration}
import imports.aws.glue.{GlueCatalogDatabase, GlueCrawler, GlueCrawlerS3Target}
import imports.aws.iam.{IamPolicy, IamRole, IamRolePolicyAttachment}
import imports.aws.s3.{S3Bucket, S3BucketObject}
import software.constructs.Construct

import java.io.File
import java.util.UUID
import scala.jdk.CollectionConverters._

final class SimpleDataLake(scope: Construct, id: String) extends TerraformStack(scope, id) {
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
    .create(self, "simple_data_lake_bucket")
    .bucket("simple-data-lake-bucket")
    .forceDestroy(true)
    .build()

  private val s3BucketObject: S3BucketObject = S3BucketObject.Builder
    .create(self, "object_data_bucket")
    .bucket(s3Bucket.getId)
    .key("data/movies.csv")
    .source(new File("modules/data-lake/src/main/resources/data/lake/databucket/data/movies.csv").getAbsolutePath)
    .etag(UUID.randomUUID().toString)
    .build()

  private val _: S3BucketObject = S3BucketObject.Builder
    .create(self, "object_results_bucket")
    .bucket(s3Bucket.getId)
    .key("results/")
    .build()

  private val glueCrawlerS3Target: GlueCrawlerS3Target = GlueCrawlerS3Target
    .builder()
    .path(s"${s3Bucket.getBucket}/data")
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

  private val iamPolicy: IamPolicy = IamPolicy.Builder
    .create(self, "iam_glue_role_policy")
    .name("iam-glue-role-policy")
    .policy(
      s"""{
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
    .policyArn(iamPolicy.getArn)
    .build()

  private val glueCatalogDatabase: GlueCatalogDatabase = GlueCatalogDatabase.Builder
    .create(self, "glue_catalog_database")
    .name("lab1-db")
    .locationUri(s"s3://${s3Bucket.getBucket}/data")
    .build()

  private val _: GlueCrawler = GlueCrawler.Builder
    .create(self, "glue_crawler")
    .name("lab1-crawler")
    .s3Target(List(glueCrawlerS3Target).asJava)
    .role(iamGlueRole.getId)
    .databaseName(glueCatalogDatabase.getName)
    .tablePrefix("movies_")
    .build()

  private val athenaWorkgroupConfigurationResultConfiguration: AthenaWorkgroupConfigurationResultConfiguration = imports.aws.athena.AthenaWorkgroupConfigurationResultConfiguration
    .builder()
    .outputLocation("s3://simple-data-lake-bucket/results/")
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

}

// saml2aws login
// sbt "data-lake/runMain io.github.mvillafuertem.data.lake.SimpleDataLake"
// yarn --cwd modules/data-lake/ planLake
// yarn --cwd modules/data-lake/ applyLake
// yarn --cwd modules/data-lake/ destroyLake
object SimpleDataLake extends App {

  private val app: cdktf.App = new cdktf.App(
    AppOptions
      .builder()
      .stackTraces(false)
      .outdir("modules/data-lake/src/main/resources/")
      .context(
        Map(
          "excludeStackIdFromLogicalIds" -> true,
          "allowSepCharsInLogicalIds"    -> true
        ).asJava
      )
      .build()
  )

  new SimpleDataLake(app, "simple-data-lake")
  app.synth()

}
