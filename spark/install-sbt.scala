#!/usr/bin/env scala

import java.nio.file.{Files, Path}
import sys.process._

object DebianContainer {
  // converts an Int into an Option around Unit representing a process
  // success or failure
  implicit def liftRV(rv: Int): Option[Unit] = rv match {
    case 0 => Some(())
    case _ => None
  }

  def main(args: Array[String]): Unit = {
    val tmpDir = Files.createTempDirectory("sbt-")
    val cwd = Some(new java.io.File(tmpDir.toString))
    for {
      _ <- Process("wget " +
        "https://dl.bintray.com/sbt/native-packages/sbt/0.13.13/sbt-0.13.13.tgz",
        cwd).!
      _ <- Process("tar xf sbt-0.13.13.tgz", cwd).!
      _ <- Seq("sbt", "sbt-launch.jar", "sbt-launch-lib.bash").map{file =>
        Process(
          s"sudo mv sbt-launcher-packaging-0.13.13/bin/$file /usr/local/bin/",
          cwd).!
      }.foldLeft(0)((acc, rt) => if (rt != 0) rt else acc)
      _ <- cwd.map{dir => (s"rm -rf $dir").!}
    } yield ()
  }
}
