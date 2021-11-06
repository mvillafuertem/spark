package io.github.mvillafuertem.data.lake.cdktf

import com.hashicorp.cdktf
import com.hashicorp.cdktf.{AppOptions, TerraformStack}
import imports.aws.AwsProvider
import software.constructs.Construct

import scala.jdk.CollectionConverters._

final class SimpleDataLake(scope: Construct, id: String) extends TerraformStack(scope, id) {
  self: Construct =>

  private val accountId = ""
  private val region = ""
  private val sharedCredentialsFile = ""
  private val profile = ""

  private val _: AwsProvider = AwsProvider.Builder
    .create(self, "cdktf_aws_provider")
    .allowedAccountIds(List(accountId).asJava)
    .region(region)
    .sharedCredentialsFile(sharedCredentialsFile)
    .profile(profile)
    .build()

}


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

