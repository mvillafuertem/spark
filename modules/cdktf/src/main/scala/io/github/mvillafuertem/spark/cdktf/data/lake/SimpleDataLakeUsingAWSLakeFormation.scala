package io.github.mvillafuertem.spark.cdktf.data.lake

import com.hashicorp.cdktf
import com.hashicorp.cdktf.{AppOptions, TerraformStack}
import software.constructs.Construct

import scala.jdk.CollectionConverters._

class SimpleDataLakeUsingAWSLakeFormation (scope: Construct, id: String) extends TerraformStack(scope, id) {
  self: Construct =>

}

// saml2aws login
// sbt "cdktf/runMain io.github.mvillafuertem.data.lake.cdktf.SimpleDataLakeUsingAWSLakeFormation"
// yarn --cwd modules/cdktf/ planLake
// yarn --cwd modules/cdktf/ applyLake
// yarn --cwd modules/cdktf/ destroyLake
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

  new SimpleDataLakeUsingAWSLakeFormation(app, "simple-data-lake")
  app.synth()

}
