package jdoop

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


import Constants._
import CPUCoresUtil._
import GetContainerCores.CoreSet
import java.io.File
import scala.util.matching.Regex
import sys.process._

/**
  * An abstract class describing a Spark task for a tool.
  */
abstract class RunTask(task: Task) {

  val containerOpts = List(
    "--clear-env",
    "--set-var", javaToolOptions
  )

  val baseContainerName: String
  val benchmarkDir = "/benchmark"
  val workDir = "/work"
  val lxcUser = "debian"
  val toolDir: String
  val testsDir = "tests"
  val relativeSrcDir = "src/src/main/java"
  val relativeBinDir = "bin"
  val dependencyLibs = recursiveListFiles(
    new File(Main.mkFilePath(task.hostBenchmarkDir, s"/$relativeBinDir/lib")),
    """.*\.jar""".r
  ).mkString(":").replaceAll(task.hostBenchmarkDir, benchmarkDir)

  implicit val containerName: String = task.containerName

  /**
    * A command to start the tool in the container as a regular user.
    */
  val innerToolCmd: Seq[String]

  /**
    *  Recursively searches all files matching a regular expression.
    *
    *  @param f A root directory where the search should start
    *  @param r A regular expression matching names of files to be returned
    *  @return A stream of files found by the search
    */
  private def recursiveListFiles(f: File, r: Regex): Stream[File] = {
    require(f.isDirectory())

    val currentDirFiles = f.listFiles.toStream
    val matching = currentDirFiles.filter(
      f => r.findFirstIn(f.getName).isDefined)
      .toStream
    matching append currentDirFiles.filter(_.isDirectory)
      .flatMap(recursiveListFiles(_, r))
  }

  /**
    * Converts an Int into an Option around Unit representing a
    * process success or failure.
    */
  implicit def liftRV(rv: Int): Option[Unit] = rv match {
    case 0 => Some(())
    case _ => None
  }

  /**
    * Runs a command in a container.
    */
  def in_container(cmd: String)(implicit container: String): Int =
    (s"sudo lxc-attach " + containerOpts.mkString(" ") +
      s" --name $container -- " + cmd).!

  /**
    * Runs a command in a container.
    */
  def in_containerSeq(cmdArgs: List[String])(implicit container: String): Int = {
    val lxcCmd = List("sudo", "lxc-attach") ++ containerOpts ++
    List("--name", container, "--")
    Process(lxcCmd ++ cmdArgs) !
  }

  /**
    * Starts a container tailored to the tool.
    */
  protected def startContainer(cores: CoreSet): Option[Unit] = for {
    _ <- s"sudo chown 1000:1000 ${task.hostWorkDir}".!
    // create an ephemeral container for jdoop with an overlay fs
    _ <- (s"sudo lxc-copy --ephemeral --name $baseContainerName " +
      s"--newname ${task.containerName} " +
      s"--mount bind=${task.hostBenchmarkDir}:$benchmarkDir:ro," +
      s"bind=${task.hostWorkDir}:$workDir").!
    // sleep for a few seconds to make sure a network device is
    // ready
    _ <- "sleep 5s".!
    // Constraining CPU and memory usage
    _ <- (s"sudo lxc-cgroup --name ${task.containerName} " +
      s"cpuset.cpus " + cores.mkString(",")).!
    _ <- (s"sudo lxc-cgroup --name ${task.containerName} " +
      s"memory.limit_in_bytes $memoryPerContainer").!
    _ <- s"sudo lxc-info --name ${task.containerName} --state".!
  } yield ()

  /**
    * Runs the task.
    */
  def run(): Unit = {
    s"mkdir -p ${task.hostWorkDir}".!

    val toolCmd = List(
      "su", "--login", "-c",
      innerToolCmd mkString " ",
      lxcUser
    )

    val containerCores = GetContainerCores(coresPerContainer)

    for {
      _ <- startContainer(containerCores)
      _ <- in_containerSeq(toolCmd)
    } yield ()

    // Stop the container (this will also destroy it because it is
    // ephemeral). We are running this outside the for comprehension
    // to make sure the container is destroyed.
    s"sudo lxc-stop --name ${task.containerName}".!

    ReleaseContainerCores(containerCores)

    s"sudo chown -R ${System.getenv("USER")}: ${task.hostWorkDir}".!
  }
}

object RunTask {

  def apply(tool: Tool)(task: Task): Unit = (tool match {
    case JDoop    => new RunJDoopTask(task)
    case Randoop  => new RunRandoopTask(task)
    case EvoSuite => new RunEvoSuiteTask(task)
  }).run()

  protected class RunJDoopTask(task: Task) extends RunTask(task) {

    val jDoopDependencyDir = s"/home/$lxcUser/jdoop-project"
    val toolDir = s"/home/$lxcUser/jdoop"
    val baseContainerName = "jdoop"

    // A command for starting JDoop on the benchmark
    val innerToolCmd = Seq(
      "cd",
      workDir,
      "&&",
      "python",
      s"$toolDir/jdoop.py",
      "--root",
      Main.mkFilePath(benchmarkDir, relativeSrcDir),
      "--timelimit", task.timelimit.toString,
      "--jpf-core-path", s"$jDoopDependencyDir/jpf-core",
      "--jdart-path", s"$jDoopDependencyDir/jdart",
      "--sut-compilation",
      Main.mkFilePath(benchmarkDir, relativeBinDir),
      "--test-compilation", testsDir,
      "--junit-path", s"$toolDir/lib/junit4.jar",
      "--hamcrest-path", s"$toolDir/lib/hamcrest-core-1.3.jar",
      "--randoop-path", s"$toolDir/lib/randoop.jar",
      "--jacoco-path", s"$toolDir/lib/jacocoant.jar",
      "--generate-report"
    ) ++
    (if (dependencyLibs != "") Seq("--classpath", dependencyLibs) else Seq())
  }

  // TODO: Actually implement the two classes
  private class RunRandoopTask(task: Task) extends RunJDoopTask(task)
  private class RunEvoSuiteTask(task: Task) extends RunJDoopTask(task)
}

