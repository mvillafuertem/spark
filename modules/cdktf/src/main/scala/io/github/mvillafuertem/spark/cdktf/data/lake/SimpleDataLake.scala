package io.github.mvillafuertem.spark.cdktf.data.lake

import com.hashicorp.cdktf
import com.hashicorp.cdktf.{AppOptions, TerraformStack}
import imports.aws.AwsProvider
import imports.aws.athena.AthenaDatabase
import imports.aws.glue.{GlueCrawler, GlueCrawlerS3Target}
import imports.aws.iam.{IamPolicy, IamRole, IamRolePolicy, IamRolePolicyAttachment}
import imports.aws.s3.{S3Bucket, S3BucketObject}
import software.constructs.Construct

import java.io.File
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
    .build()

  private val s3BucketObject: S3BucketObject = S3BucketObject.Builder
    .create(self, "object_data_bucket")
    .bucket(s3Bucket.getId)
    .key("data/movies.csv")
    .source(new File("modules/cdktf/src/main/resources/data/lake/databucket/data/movies.csv").getAbsolutePath)
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
                         |      "Action": "sts:AssumeRole",
                         |      "Principal": {
                         |        "Service": "glue.amazonaws.com"
                         |      },
                         |      "Effect": "Allow",
                         |      "Sid": ""
                         |    }
                         |  ]
                         |}""".stripMargin)
    .build()

  private val iamPolicy: IamPolicy = IamPolicy.Builder
    .create(self, "iam_glue_role_policy")
    .name("iam-glue-role-policy")
    //.role(iamGlueRole.getId)
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
         |      "Resource": ["{${s3Bucket.getArn}}"]
         |    },
         |    {
         |      "Effect": "Allow",
         |      "Action": [
         |        "s3:PutObject",
         |        "s3:GetObject",
         |        "s3:DeleteObject"
         |      ],
         |      "Resource": ["{${s3Bucket.getArn}}/*"]
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

  private val iamRolePolicyAttachment: IamRolePolicyAttachment = IamRolePolicyAttachment.Builder
    .create(self, "iam_glue_role_policy_attachment")
    .role(iamGlueRole.getId)
    .policyArn(iamPolicy.getArn)
    .build()

  private val _: GlueCrawler = GlueCrawler.Builder
    .create(self, "glue_crawler")
    .name("lab1-crawler")
    .s3Target(List(glueCrawlerS3Target).asJava)
    .role(iamGlueRole.getId)
    .databaseName("lab1-db")
    .tablePrefix("movies_")
    .build()

}

// saml2aws login
// sbt "cdktf/runMain io.github.mvillafuertem.data.lake.cdktf.SimpleDataLake"
// yarn --cwd modules/cdktf/ planLake
// yarn --cwd modules/cdktf/ applyLake
// yarn --cwd modules/cdktf/ destroyLake
object SimpleDataLake extends App {

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

  new SimpleDataLake(app, "simple-data-lake")
  app.synth()

}
