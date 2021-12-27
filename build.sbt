import sbt.Def

import scala.{ Console => csl }

Global / onLoad := {
  val GREEN = csl.GREEN
  val RESET = csl.RESET
  println(s"""$GREEN
             |$GREEN        ███████╗ ██████╗   █████╗  ██████╗  ██╗  ██╗
             |$GREEN        ██╔════╝ ██╔══██╗ ██╔══██╗ ██╔══██╗ ██║ ██╔╝
             |$GREEN        ███████╗ ██████╔╝ ███████║ ██████╔╝ █████╔╝
             |$GREEN        ╚════██║ ██╔═══╝  ██╔══██║ ██╔══██╗ ██╔═██╗
             |$GREEN        ███████║ ██║      ██║  ██║ ██║  ██║ ██║  ██╗
             |$GREEN        ╚══════╝ ╚═╝      ╚═╝  ╚═╝ ╚═╝  ╚═╝ ╚═╝  ╚═╝
             |$RESET        v.${version.value}
             |""".stripMargin)
  (Global / onLoad).value
}

val scala213 = "2.13.7"

lazy val spark = (project in file("."))
  .aggregate(
    cdktf
  )
  .settings(
    scalaVersion := scala213,
    welcomeMessage
  )

lazy val cdktf = (project in file("modules/cdktf"))
  .settings(scalaVersion := scala213)
  .settings(watchTriggers += baseDirectory.value.toGlob / "*.scala")

lazy val `data-lake` = (project in file("modules/data-lake"))
  .settings(scalaVersion := scala213)
  .dependsOn(cdktf)

lazy val `data-warehouse` = (project in file("modules/data-warehouse"))
  .settings(scalaVersion := scala213)
  .dependsOn(cdktf)

lazy val `deep-learning` = (project in file("modules/deep-learning"))
  .settings(scalaVersion := scala213)
  .dependsOn(cdktf)

lazy val `map-reduce` = (project in file("modules/map-reduce"))
  .settings(scalaVersion := scala213)
  .dependsOn(cdktf)

def welcomeMessage: Def.Setting[String] = onLoadMessage := {
  def header(text: String): String                = s"${csl.BOLD}${csl.MAGENTA}$text${csl.RESET}"
  def cmd(text: String, description: String = "") = f"${csl.GREEN}> ${csl.CYAN}$text%10s $description${csl.RESET}"
  //def subItem(text: String): String = s"  ${Console.YELLOW}> ${Console.CYAN}$text${Console.RESET}"

  s"""|${header("sbt")}:
      |${cmd("build", "- Prepares sources, compiles and runs tests")}
      |${cmd("prepare", "- Prepares sources by applying both scalafix and scalafmt")}
      |${cmd("fmt", "- Formats source files using scalafmt")}
      |${cmd("cdktf/run", "- Create cdk.tf.json files")}
      |
      |${header("yarn")}:
      |${cmd("--cwd modules/cdktf/ install")}
      |${cmd("--cwd modules/cdktf/ get")}
      """.stripMargin
}
