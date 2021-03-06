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
// along with jdoop-wrapper.  If not, see <http://www.gnu.org/licenses/>.


import Constants._
import CPUCoresUtil._
import GetContainerCores.CoreSet
import java.io.{File, PrintWriter}
import scala.language.implicitConversions
import scala.language.postfixOps
import scala.util.matching.Regex
import sys.process._

/**
  * An abstract class describing a Spark task for a tool.
  */
abstract class RunTask(task: Task) {

  /**
    * The name of a container configured for a tool.
    */
  val baseContainerName: String
  /**
    * A command to start the tool in the container as a regular user.
    */
  val innerToolCmd: Seq[String]

  /**
    * Regular expressions that describe paths to directories with test
    * cases (in both source and binary forms).
    */
  val testDirRegexs: Set[Regex]

  val containerOpts = List(
    "--clear-env",
    "--set-var", javaToolOptions
  )

  val benchmarkDir = "/benchmark"
  val workDir = "/work"
  val lxcUser = "debian"
  val testsDir = "tests"
  val relativeSrcDir = "src/src/main/java"
  val relativeBinDir = "bin"
  val dependencyLibs = recursiveListFiles(
    new File(Main.mkFilePath(task.hostBenchmarkDir, s"/$relativeBinDir/lib")),
    """.*\.jar""".r
  ).mkString(":").replaceAll(task.hostBenchmarkDir, benchmarkDir)

  implicit val containerName: String = task.containerName

  val totalTimeFilePath = Main.mkFilePath(task.hostWorkDir, "total-time.txt")

  /**
    *  Recursively searches all files matching a regular expression.
    *
    *  @param f A root directory where the search should start
    *  @param r A regular expression matching names of files to be returned
    *  @return A stream of files found by the search
    */
  protected def recursiveListFiles(f: File, r: Regex): Stream[File] = {
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
    * Prints to a file.
    */
  def printToFile(f: File)(op: PrintWriter => Unit): Unit = {
    val p = new PrintWriter(f)
    try { op(p) } finally { p.close() }
  }

  /**
    * Runs the task.
    */
  def run(): Unit = {
    val startTime = System.nanoTime()
    System.err.println(s"Starting task ${task.project.projectDir}")

    // Remove any left-overs in case this is a re-attempt of the task
    s"sudo lxc-destroy --force --name ${task.containerName}".!
    s"sudo rm -rf ${task.hostWorkDir}".!

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
    // Stop and destroy the container. We are running this outside the
    // for comprehension to make sure the container is destroyed.
    s"sudo lxc-destroy --force --name ${task.containerName}".!
    ReleaseContainerCores(containerCores)
    s"sudo chown -R ${System.getenv("USER")}: ${task.hostWorkDir}".!
    s"sudo chmod -R u+rwx ${task.hostWorkDir}".!

    // Record the number of test cases (failed and total) and also
    // those that were generated by Randoop and by JDart
    val testCaseCountMap = Map[String, Int](
      "failed-test-case-count.txt"  -> failedTestCaseCount,
      "randoop-test-case-count.txt" -> randoopTestCaseCount,
      "jdart-test-case-count.txt"   -> jdartTestCaseCount,
      "test-case-count.txt"         -> totalTestCaseCount
    )
    testCaseCountMap foreach { case (fileName, count) =>
      try {
        val f = new File(Main.mkFilePath(task.hostWorkDir, fileName))
        f.createNewFile()
        printToFile(f){_.println(count)}
      } catch { case _: Throwable => () }
    }
    // ... and finally delete them
    try deleteGenFiles(testDirRegexs) catch { case _: Throwable => () }

    // archive JaCoCo html files
    try {
      val jacocoDir = Some(new File(
        Main.mkFilePath(task.hostWorkDir, "jacoco-site")))
      if (jacocoDir.get.exists) {
        Process(Seq("tar", "czf", "html.tar.gz", "html"), jacocoDir).!
        Process(Seq("rm", "-rf", "html"), jacocoDir).!
      }
    } catch { case _: Throwable => () }

    // record the total time spent on this task and write it to a file
    try {
      val totalTime = (System.nanoTime() - startTime) / 1000000000 // seconds
      val totalTimeFile = new File(totalTimeFilePath)
      totalTimeFile.createNewFile()
      printToFile(totalTimeFile){_.println(totalTime)}
    } catch { case _: Throwable => () }

    // sync everything to the master node
    Process(Seq(
      "rsync",
      "-a",
      "--delete", // for the case this is a re-attempt of the task
      task.hostWorkDir,
      s"$masterMachine:${task.masterNodeDir.getPath}"
    )).!
  }

  def allJavaFiles(dir: File): Stream[File] =
    recursiveListFiles(dir, """.*\.java""".r)

  def allClassFiles(dir: File): Stream[File] =
    recursiveListFiles(dir, """.*\.class""".r)

  /**
    * Returns the total number of test cases generated for this task.
    */
  def totalTestCaseCount: Int = randoopTestCaseCount + jdartTestCaseCount

  /**
    * Returns the number of failed test cases generated for this task.
    */
  def failedTestCaseCount: Int

  /**
    * Returns the number of failed test cases generated for this task.
    */
  def randoopTestCaseCount: Int

  /**
    * Returns the number of failed test cases generated for this task.
    */
  def jdartTestCaseCount: Int

  /**
    * Deletes generated Java source code and bytecode files in
    * directories specified by regular expressions.
    */
  def deleteGenFiles(dirs: Set[Regex]): Unit = {
    val wd = new File(task.hostWorkDir)
    dirs foreach { regex =>
      recursiveListFiles(wd, regex) foreach { dir =>
        allJavaFiles(dir) foreach { _.delete() }
        allClassFiles(dir) foreach { _.delete() }
      }
    }
  }
}

object RunTask {

  def apply(task: Task): Unit = (task.tool match {
    case JDoop    => new RunJDoopTask(task)
    case Randoop  => new RunRandoopTask(task)
  }).run()

  sealed protected class RunJDoopTask(task: Task) extends RunTask(task) {

    val jDoopDependencyDir = s"/home/$lxcUser/jdoop-project"
    val toolDir = s"/home/$lxcUser/jdoop"
    val baseContainerName = "jdoop"
    val dartTCRootDir = "darted"
    val jdartTCPrefix = "TestsTest"

    // A command for starting JDoop on the benchmark
    lazy val innerToolCmd = Seq(
      "cd",
      workDir,
      "&&",
      "python",
      s"$toolDir/jdoop.py"
    ) ++
    (if (task.tool == Randoop) Seq("--randoop-only") else Seq()) ++
    Seq(
      "--root",
      Main.mkFilePath(benchmarkDir, relativeSrcDir),
      "--timelimit", task.timelimit.toString,
      "--randoop-time", "540",
      "--jdart-time", "60",
      "--jpf-core-path", s"$jDoopDependencyDir/jpf-core",
      "--jdart-path", s"$jDoopDependencyDir/jdart",
      "--sut-compilation",
      Main.mkFilePath(benchmarkDir, relativeBinDir),
      "--test-compilation", testsDir,
      "--junit-path", s"$toolDir/lib/junit4.jar",
      "--hamcrest-path", s"$toolDir/lib/hamcrest-core-1.3.jar",
      "--randoop-path", s"$toolDir/lib/randoop.jar",
      "--jacoco-path", s"$toolDir/lib/jacocoant.jar",
      "--generate-report",
      "--benchmark-id", task.project.projectDir
    ) ++
    (if (dependencyLibs != "") Seq("--classpath", dependencyLibs) else Seq())

    val testDirRegexs = Set(
      """tests""".r,
      """tests-round-[0-9]+""".r,
      """randooped[0-9]+""".r,
      """darted""".r
    )

    protected def isRandoopTestCaseFile: File => Boolean = f => {
      val name = f.getName
      !name.contains("_e") &&            // Exclude Randoop test suites files,
        !name.contains(jdartTCPrefix) && // test case and suite files
                                         // generated by JDart
        name != "ErrorTest.java"         // An error test suite file
    }

    protected def countDartTestCases: File => Int = f => {
      val source = scala.io.Source.fromFile(f.getPath)
      val lines = try source.mkString.split("\n").toSeq finally source.close()
      lines.filter{_.contains("public void test")}.length
    }

    def randoopTestCaseCount: Int =
      allJavaFiles(new File(task.hostWorkDir))
        .filter{isRandoopTestCaseFile}.length

    def jdartTestCaseCount: Int = {
      val jdartTCRootDir = new File(
        Main.mkFilePath(task.hostWorkDir, dartTCRootDir)
      )
      // Here we are assuming that all test cases generated by JDart
      // are in files named TestsTest10.java.
      if (jdartTCRootDir.exists)
        recursiveListFiles(jdartTCRootDir, (jdartTCPrefix + "10.java").r)
          .map{countDartTestCases}
          .foldLeft(0)(_ + _)
      else 0
    }

    def failedTestCaseCount: Int = {
      val errorSuiteFiles = recursiveListFiles(
        new File(task.hostWorkDir),
        """ErrorTest.java""".r
      )

      errorSuiteFiles.map{f =>
        val source = scala.io.Source.fromFile(f.getPath)
        val lines = try source.mkString.split("\n").toSeq finally source.close
        lines.filter{_.startsWith("ErrorTest")}.length
      }.sum
    }
  }

  private class RunRandoopTask(task: Task) extends RunJDoopTask(task)
}
