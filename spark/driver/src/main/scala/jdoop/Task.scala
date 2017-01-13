package jdoop

case class SF110Project(projectDir: String)

case class Task(
  project: SF110Project,
  containerName: String,
  timelimit: Int,
  hostBenchmarkDir: String,
  hostWorkDir: String)
