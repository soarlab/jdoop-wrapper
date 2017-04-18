name := "jdoop-spark-application"

version := "1.0"

scalaVersion := "2.11.8"

scalacOptions := Seq("-feature")

libraryDependencies ++= Seq(
  "org.apache.spark" %% "spark-core" % "2.1.0" % "provided"
)
