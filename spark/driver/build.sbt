name := "jdoop-spark-application"

version := "1.0"

scalaVersion := "2.11.6"

libraryDependencies ++= Seq(
  "org.apache.spark" %% "spark-core" % "2.0.2" % "provided"
)

// assemblyMergeStrategy in assembly := {
//   case PathList("javax", "servlet", xs @ _*) => MergeStrategy.first
//   case x => (assemblyMergeStrategy in assembly).value(x)
// }
