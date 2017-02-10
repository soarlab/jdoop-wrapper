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
import java.io._
import org.apache.spark.{SparkConf, SparkContext}
import org.apache.spark.rdd.RDD
import sys.process._

object Main {

  def mkFilePath(xs: String*): String = new File(xs.mkString("/")).getPath

  def runSF100JDoopTask(task: Task): Unit = {
    import Constants._
    import CPUCoresUtil._
    import java.io.File
    import java.nio.file.{Files, Path}
    import scala.util.matching.Regex
    import sys.process._

    /**
      *  Recursively searches all files matching a regular expression.
      *
      *  @param f A root directory where the search should start
      *  @param r A regular expression matching names of files to be returned
      *  @return A stream of files found by the search
      */
    def recursiveListFiles(f: File, r: Regex): Stream[File] = {
      require(f.isDirectory())

      val currentDirFiles = f.listFiles.toStream
      val matching = currentDirFiles.filter(
        f => r.findFirstIn(f.getName).isDefined)
        .toStream
      matching append currentDirFiles.filter(_.isDirectory)
        .flatMap(recursiveListFiles(_, r))
    }

    // converts an Int into an Option around Unit representing a process
    // success or failure
    implicit def liftRV(rv: Int): Option[Unit] = rv match {
      case 0 => Some(())
      case _ => None
    }

    // runs a command in a container
    def in_container(cmd: String)(implicit container: String): Int =
      (s"sudo lxc-attach --clear-env --name $container -- " + cmd).!

    // runs a command in a container
    def in_containerSeq(cmdArgs: List[String])(implicit container: String): Int = {
      lazy val lxcCmd = List("sudo", "lxc-attach", "--clear-env", "--name",
        container, "--")
      Process(lxcCmd ++ cmdArgs) !
    }

    val baseContainerName = "jdoop"
    val benchmarkDir = "/benchmark"
    val workDir = "/work"
    val lxcUser = "debian"
    val jdoopDir = s"/home/$lxcUser/jdoop"
    val jDoopDependencyDir = s"/home/$lxcUser/jdoop-project"
    val testsDir = "tests"
    val relativeSrcDir = "src/src/main/java"
    val relativeBinDir = "bin"
    val dependencyLibs = recursiveListFiles(
      new File(mkFilePath(task.hostBenchmarkDir, s"/$relativeBinDir/lib")),
      """.*\.jar""".r
    ).mkString(":").replaceAll(task.hostBenchmarkDir, benchmarkDir)
    s"mkdir -p ${task.hostWorkDir}".!

    implicit val containerName: String = task.containerName

    // A command for starting JDoop on the benchmark
    val innerJDoopCmd = Seq(
      "cd",
      workDir,
      "&&",
      "python",
      s"$jdoopDir/jdoop.py",
      "--root",
      mkFilePath(benchmarkDir, relativeSrcDir),
      "--timelimit", task.timelimit,
      "--jpf-core-path", s"$jDoopDependencyDir/jpf-core",
      "--jdart-path", s"$jDoopDependencyDir/jdart",
      "--sut-compilation",
      mkFilePath(benchmarkDir, relativeBinDir),
      "--test-compilation", testsDir,
      "--junit-path", s"$jdoopDir/lib/junit4.jar",
      "--hamcrest-path", s"$jdoopDir/lib/hamcrest-core-1.3.jar",
      "--randoop-path", s"$jdoopDir/lib/randoop.jar",
      "--jacoco-path", s"$jdoopDir/lib/jacocoant.jar",
      "--generate-report"
    ) ++
    (if (dependencyLibs != "") Seq("--classpath", dependencyLibs) else Seq())
    val jdoopCmd = List(
      "su", "--login", "-c",
      innerJDoopCmd mkString " ",
      lxcUser
    )

    val containerCores = GetContainerCores(coresPerContainer)

    for {
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
        s"cpuset.cpus " + containerCores.mkString(",")).!
      _ <- (s"sudo lxc-cgroup --name ${task.containerName} " +
        s"memory.limit_in_bytes $memoryPerContainer").!
      // disable the swap memory in the container
      // _ <- (s"sudo lxc-cgroup --name ${task.containerName} " +
      //   s"memory.memsw.limit_in_bytes 0").!
      _ <- s"sudo lxc-info --name ${task.containerName} --state".!
      _ <- in_containerSeq(jdoopCmd)
    } yield ()

    // Stop the container (this will also destroy it because it is
    // ephemeral). We are running this outside the for comprehension
    // to make sure the container is destroyed.
    s"sudo lxc-stop --name ${task.containerName}".!

    ReleaseContainerCores(containerCores)

    s"sudo chown -R ${System.getenv("USER")}: ${task.hostWorkDir}".!
  }

  /**
    * Creates result directories if they don't exist and makes sure no
    * prior benchmark results for the same benchmarks are in way.
    * @param loc A map of machines and result directories
    * @param benchmarkList A list of benchmarks
    * @return False if some benchmark result directories already exist, 
    *         true otherwise.
    */
  def initResDirs(loc: Map[String, String],
    benchmarkList: Seq[String]): Boolean =
    (loc foldLeft false) { (failed, kv) =>
      val (machine, dir) = kv
      s"ssh $machine mkdir -p $dir".!
      failed || (benchmarkList foldLeft false){ (bFailed, benchmarkDir) =>
        bFailed || ((s"ssh $machine file -E $dir/$benchmarkDir".!) == 0)
      }
    }

  def pullResults(loc: Map[String, String], localDir: String): Unit = {
    s"mkdir -p $localDir".!
    loc foreach { case (machine, dir) =>
      println(s"Pulling results from $machine...")
      s"rsync -a $machine:$dir/ $localDir/".!
    }
  }

  def pushCgroupsFile(machines: Set[String], path: String): Unit = {
    CPUCoresUtil.generateFile()
    machines foreach { m => s"rsync -a $path $m:$path".! }
  }

  def parallelizeBenchmarks(benchmarkList: Seq[String], sc: SparkContext,
    timelimit: Int, sfRoot: String, sfResultsRoot: String): RDD[Task] =
    sc.parallelize(
      benchmarkList map { b => Task(
        project = SF110Project(b),
        containerName = b,
        timelimit = timelimit,
        hostBenchmarkDir = mkFilePath(sfRoot, b),
        hostWorkDir = mkFilePath(sfResultsRoot, b))
      },
      benchmarkList.length // the number of partitions of the data
    )

  def usage(): Unit = {
    println("One argument needed: <list-of-benchmarks.txt>")
    println("Optional argument: <time limit> in seconds")
    println("Optional argument: <name> for the experiment name")
    sys.exit(1)
  }

  def main(args: Array[String]): Unit = {
    if (args.length < 1 || args.length > 3)
      usage()

    val source = scala.io.Source.fromFile(args(0))
    val benchmarkList =
      try source.mkString.split("\n").toSeq finally source.close()
    val timelimit =
      if (args.length >= 2)
        try args(1).toInt catch {case _: Throwable => usage(); 0}
        // 0 is at the end of the catch block in order to make the
        // block's type be Int so it lines up with the type of the try
        // block.
      else defaultTimelimit // the default time limit of 30 seconds
    val experimentName =
      if (args.length >= 3)
        args(2)
      else
        new java.text.SimpleDateFormat("yyyy-MM-DD-HH-mm-ss").format(
          new java.util.Date())
    val resultsSuffixDir = mkFilePath(experimentName, timelimit.toString)
    val fullScracthResultsDir = mkFilePath(scratchResultsRoot, resultsSuffixDir)

    val loc = workerMachines.foldLeft(Map[String, String]()){
      (map, machine) => map +
      (machine -> fullScracthResultsDir)
    }
    val failed = initResDirs(
      loc + ("localhost" -> fullScracthResultsDir),
      benchmarkList
    )
    if (failed) {
      println("Remove existing colliding benchmark directories first!")
      sys.exit(1)
    }

    pushCgroupsFile(loc.keySet, cpuCoresFilePath)

    val conf = new SparkConf().setAppName("JDoop Executor")
    val sc = new SparkContext(conf)

    val distBenchmarks = parallelizeBenchmarks(
      benchmarkList,
      sc,
      timelimit,
      sfRoot,
      fullScracthResultsDir
    )

    val r = distBenchmarks.map{runSF100JDoopTask}.reduce{(_, _) => ()}
    println(r) // I'm not sure if this is needed, but in case it is,
               // it will enforce 'r' to be evaluated.
    sc.stop()

    pullResults(
      loc + ("localhost" -> fullScracthResultsDir),
      mkFilePath(finalResultsRoot, resultsSuffixDir)
    )

    println("Results are available in: " +
      mkFilePath(finalResultsRoot, resultsSuffixDir))
  }
}
