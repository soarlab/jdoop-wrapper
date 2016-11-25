package jdoop

case class Task(
  pkg: SourcePackage,
  containerName: String,
  baseDir: String,
  timelimit: Int)
