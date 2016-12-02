#!/usr/bin/env scala

import sys.process._

// This script takes SF110 benchmarks organized in two folders SF110
// and SF110-src, gets into each of them, and "zips" it together into
// a single root directory.

object ZipBenchmarks {
  // converts an Int into an Option around Unit representing a process
  // success or failure
  implicit def liftRV(rv: Int): Option[Unit] = rv match {
    case 0 => Some(())
    case _ => None
  }

  def zipDirs(
    project: String,
    srcDir: String,
    binDir: String,
    targetRootDir: String): Unit = {

    val targetDir = Some(new java.io.File(targetRootDir))
    val targetBinDir = Some(new java.io.File(
      Seq(targetRootDir, project, "bin").mkString("/")))
    val jarFile = project.split("_")(1) + ".jar"

    for {
      _ <- s"mkdir -p $targetRootDir".!
      _ <- Process(Seq("mkdir", "-p", project + "/src"), targetDir).!
      _ <- Process(Seq("mkdir", "-p", project + "/bin"), targetDir).!
      _ <- (Process(Seq(
        "cp",
        "-aT",
        srcDir + "/" + project + "/",
        targetRootDir + "/" + project + "/src/"
      )) #|| Process("true")).!
      _ <- (Process(Seq(
        "cp",
        "-aT",
        binDir + "/" + project + "/",
        targetRootDir + "/" + project + "/bin/"
      )) #|| Process("true")).!
      _ <- Process(Seq("jar", "xf", jarFile), targetBinDir).!
    } yield ()
  }

  def main(args: Array[String]): Unit = {
    if (args.length != 4) {
      """Four parameters are needed:
         - <srcDir> is an absolute path to the root source directory
         - <binDir> is an absolute path to the root binary directory
         - <list> is a path to a text file with list of benchmarks to be zipped
         - <targetDir> is the root directory where zipping should happen""".
        split("\n").map{_.trim}.foreach{println}
      sys.exit(1)
    }

    val srcDir        = args(0)
    val binDir        = args(1)
    val listFile      = args(2)
    val targetRootDir = args(3)

    val source = scala.io.Source.fromFile(listFile)
    val listOfProjects = try source.mkString.split("\n").toSeq finally source.close()

    listOfProjects.foreach{zipDirs(_, srcDir, binDir, targetRootDir)}
  }
}
