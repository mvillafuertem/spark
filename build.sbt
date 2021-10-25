Global / onLoad := {
  import scala.Console
  val GREEN = Console.GREEN
  val RESET = Console.RESET
  println(
    s"""$GREEN
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

lazy val spark = (project in file("."))
  .aggregate(
    cdktf,
    notebooks
  )
  .settings(
    scalaVersion := "2.13.6",
    welcomeMessage
  )


lazy val cdktf = (project in file("modules/cdktf"))
  .settings(scalaVersion := "2.13.6")

lazy val notebooks = (project in file("modules/notebooks"))
  .settings(scalaVersion := "2.13.6")


def welcomeMessage = onLoadMessage := {
  import scala.Console
  def header(text: String): String = s"${Console.BOLD}${Console.MAGENTA}$text${Console.RESET}"
  def cmd(text: String): String = s"${Console.GREEN}> ${Console.CYAN}$text${Console.RESET}"
  //def subItem(text: String): String = s"  ${Console.YELLOW}> ${Console.CYAN}$text${Console.RESET}"

  s"""|${header("sbt")}:
      |${cmd("build")}       - Prepares sources, compiles and runs tests
      |${cmd("prepare")}     - Prepares sources by applying both scalafix and scalafmt
      |${cmd("fmt")}         - Formats source files using scalafmt
      |${cmd("cdktf/run")}   - Create cdk.tf.json files
      |
      |${header("yarn")}:
      |${cmd("--cwd modules/cdktf/ install")}
      |${cmd("--cwd modules/cdktf/ get")}
      """.stripMargin
}