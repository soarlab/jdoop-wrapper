#!/usr/bin/env scala

// Copyright 2017 Marko Dimjašević
//
// This file is part of jdoop-wrapper.
//
// jdoop-wrapper is free software: you can redistribute it and/or modify it
// under the terms of the GNU Affero General Public License as
// published by the Free Software Foundation, either version 3 of the
// License, or (at your option) any later version.
//
// jdoop-wrapper is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU Affero General Public License for more details.
//
// You should have received a copy of the GNU Affero General Public License
// along with jdoop-wrapper.  If not, see <http://www.gnu.org/licenses/>.


import java.nio.file.{Files, Path}
import sys.process._

object InstallSBT {
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
