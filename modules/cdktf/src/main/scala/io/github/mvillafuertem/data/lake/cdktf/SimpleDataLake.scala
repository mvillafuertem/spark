package io.github.mvillafuertem.data.lake.cdktf

import com.hashicorp.cdktf
import com.hashicorp.cdktf.{ AppOptions, TerraformStack }
import imports.aws.AwsProvider
import imports.aws.ec2.Instance
import software.constructs.Construct

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

  private val _: Instance = Instance.Builder
    .create(self, "hello")
    .ami("ami-0da7ba92c3c072475")
    .instanceType("t2.micro")
    .tags(
      Map(
        "Name" -> "hello"
      ).asJava
    )
    .build()

}

// saml2aws login
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
