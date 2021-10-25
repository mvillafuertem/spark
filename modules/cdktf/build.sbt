libraryDependencies ++= Seq(
  // C D K T F
  "com.hashicorp" % "cdktf" % "0.7.0",
  "software.constructs" % "constructs" % "10.0.9"
) ++ Seq(
  // C D K T F  T E S T
  "org.scalatest" %% "scalatest" % "3.2.10" % Test
)