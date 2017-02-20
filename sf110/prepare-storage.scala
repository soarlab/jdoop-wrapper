#!/bin/sh
export SCRIPT_PATH=$(readlink -f "$0")
exec scala "$0" "$@"
!#

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
// along with maline.  If not, see <http://www.gnu.org/licenses/>.


import java.io.File
import sys.process._

object PrepareStorage {

  val scriptPath  = System.getenv("SCRIPT_PATH")
  val scriptDir   = scriptPath.substring(0, scriptPath.lastIndexOf("/"))
  val wrapperHome = scriptDir.substring(0, scriptDir.lastIndexOf("/"))

  val mountPoint = "/mnt/storage"
  val sf110Dir = s"$mountPoint/sf110"
  val sf110Release = "SF110-20130704"
  val sf110SrcArchive = s"$sf110Dir/$sf110Release-src.zip"
  val sf110BinArchive = s"$sf110Dir/$sf110Release.zip"
  val masterNode = "node-1.multinode.jpf-doop.emulab.net"

  val pkgs = """
    unzip
    """.split("\n").map{_.trim}.filter{!_.isEmpty}

  // converts an Int into an Option around Unit representing a process
  // success or failure
  implicit def liftRV(rv: Int): Option[Unit] = rv match {
    case 0 => Some(())
    case _ => None
  }

  def mkFilePath(xs: String*): String = new File(xs.mkString("/")).getPath

  def main(args: Array[String]): Unit = {
    val f = Some(new File(sf110Dir))
    for {
      _ <- s"sudo chown -R ${System.getenv("USER")}: $mountPoint".!
      _ <- s"mkdir -p $sf110Dir".!
      _ <- s"rsync -a $masterNode:$sf110SrcArchive $sf110Dir/".!
      _ <- s"rsync -a $masterNode:$sf110BinArchive $sf110Dir/".!
      _ <- s"sudo apt-get install --yes ${pkgs.mkString(" ")}".!
      _ <- Process(Seq("unzip", sf110SrcArchive), f).!
      _ <- Process(Seq("unzip", sf110BinArchive), f).!
      _ <- Process(
        Seq(
          mkFilePath(wrapperHome, "sf110", "zip-benchmarks.scala"),
          mkFilePath(sf110Dir, s"$sf110Release-src"),
          mkFilePath(sf110Dir, s"$sf110Release"),
          mkFilePath(wrapperHome, "sf110", "project-list.txt"),
          sf110Dir
        )).!
      _ <- Process(
        Seq(
          mkFilePath(wrapperHome, "sf110", "patch-sf110.scala"),
          sf110Dir
        )).!
    } yield ()
  }
}
