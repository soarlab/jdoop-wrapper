package jdoop

case class Task(
  pkg: SourcePackage,
  containerName: String,
  baseDir: String,
  timelimit: Int)

case class SF110Task(
  project: SF110Project,
  containerName: String,
  timelimit: Int,
  hostBenchmarkDir: String,
  hostWorkDir: String)
