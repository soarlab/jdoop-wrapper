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
// along with maline.  If not, see <http://www.gnu.org/licenses/>.


import sys.process._

object EvoSuiteContainer {

  val pkgs = """
    sudo
    ant
    ant-optional
    openjdk-8-jre
    openjdk-8-jre-headless
    openjdk-8-jdk
    wget
""".split("\n").map{_.trim}.filter{!_.isEmpty}

  val lxc_user = "debian"
  val evoSuiteArchiveURL = "https://github.com/EvoSuite/evosuite/releases/" +
    "download/v1.0.3/evosuite-1.0.3.jar"
  val jaCoCoArchiveURL = "https://github.com/jacoco/jacoco/releases/" +
    "download/v0.7.5/jacoco-0.7.5.201505241946.zip"

  // converts an Int into an Option around Unit representing a process
  // success or failure
  implicit def liftRV(rv: Int): Option[Unit] = rv match {
    case 0 => Some(())
    case _ => None
  }

  // runs a command in a container
  def in_container(cmd: String)(implicit container: String): Int =
    (s"sudo lxc-attach --name $container -- " + cmd).!

  // runs a command in a container
  def in_container(cmdArgs: List[String])(implicit container: String): Int = {
    lazy val lxcCmd = List("sudo", "lxc-attach", "--name", container, "--")
    Process(lxcCmd ++ cmdArgs) !
  }

  def main(args: Array[String]): Unit = {
    if (args.length != 2) {
      println("Usage: <script-name> <base> <destination>")
      println("  <base> is the name of the base container")
      println("  <destination> is the name of the new container")
      sys.exit(1)
    }

    val base = args(0)
    implicit val destination = args(1)
    val benchmarkDir = "/benchmark"

    for {
      _ <-   "sudo /etc/init.d/apparmor restart".!
      _ <-  s"sudo lxc-info --name $base --state".!
      _ <-  s"sudo lxc-copy --name $base --newname $destination".!
      _ <-  s"sudo lxc-start --name $destination".!
      _ <- in_container("/etc/init.d/networking restart")
      _ <-  s"sudo lxc-info --name $destination".!
      // Because we are running a Debian Snapshot Archive image, it
      // might be unsafe to use it due to outdated package
      // versions. Ignore this security aspect for the sake of
      // repeatability.
      _ <- in_container("apt-get -o Acquire::Check-Valid-Until=false update")
      _ <- in_container("apt-get install --yes " + pkgs.mkString(" "))
      _ <- in_container(List("su", "-c",
        "echo '%sudo ALL=(ALL) NOPASSWD:ALL' >> /etc/sudoers"))
      _ <- in_container("useradd --create-home --groups sudo --shell " +
        s"/bin/bash $lxc_user")
      _ <- in_container(List(
        "su", "--login", "-c",
        s"wget $evoSuiteArchiveURL",
        lxc_user))
      _ <- in_container(List(
        "su", "--login", "-c",
        s"wget $jaCoCoArchiveURL -O jacoco.jar",
        lxc_user))
      _ <- in_container(s"mkdir $benchmarkDir")
      _ <-  s"sudo lxc-stop --name $destination".!
      // install JaCoCo 0.7.5 too and run it once EvoSuite is over
      // (the later part comes in the Spark runner for EvoSuite)
    } yield ()
  }
}
