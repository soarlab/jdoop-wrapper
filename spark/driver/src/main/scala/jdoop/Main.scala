package jdoop

import org.apache.spark.{SparkConf, SparkContext}

object Main {

  def runSF100JDoopTask(task: Task): Unit = {
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
      (s"sudo lxc-attach --name $container -- " + cmd).!

    // runs a command in a container
    def in_containerSeq(cmdArgs: List[String])(implicit container: String): Int = {
      lazy val lxcCmd = List("sudo", "lxc-attach", "--name", container, "--")
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
      new File(task.hostBenchmarkDir + s"/$relativeBinDir/lib"),
      """.*\.jar""".r)
      .mkString(":")
      .replaceAll(task.hostBenchmarkDir, benchmarkDir)

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
      Seq(benchmarkDir, relativeSrcDir).mkString("/"),
      "--timelimit", task.timelimit,
      "--jpf-core-path", s"$jDoopDependencyDir/jpf-core",
      "--jdart-path", s"$jDoopDependencyDir/jdart",
      "--sut-compilation",
      Seq(benchmarkDir, relativeBinDir).mkString("/"),
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
      // TODO: Constrain container resources. This info should also be
      // part of Task. It's not going to work with LXD as it doesn't
      // see the regular LXC image pool.
      _ <- s"sudo lxc-info --name ${task.containerName} --state".!
      _ <- in_containerSeq(jdoopCmd)
    } yield ()

    // Stop the container (this will also destroy it because it is
    // ephemeral). We are running this outside the for comprehension
    // to make sure the container is destroyed.
    s"sudo lxc-stop --name ${task.containerName}".!

    s"sudo chown -R ${System.getenv("USER")}: ${task.hostWorkDir}".!
  }

  def usage(): Unit = {
    println("One argument needed: <list-of-benchmarks.txt>")
    println("Optional argument: <time limit> in seconds")
    sys.exit(1)
  }

  def initResDirs(remoteLoc: Map[String, String]): Unit = {
    import sys.process._

    remoteLoc foreach { case (machine, dir) =>
      s"ssh $machine rm -rf $dir".!
      s"ssh $machine mkdir -p $dir".!
    }
  }

  def pullResults(remoteLoc: Map[String, String],
    localDir: String): Unit = {
    import sys.process._

    remoteLoc foreach { case (machine, dir) =>
      println(s"Pulling results from $machine...")
      s"rsync -a $machine:$dir/ $localDir/".!
    }
  }

  def main(args: Array[String]): Unit = {
    import sys.process._

    if (args.length < 1 || args.length > 2)
      usage()

    val source = scala.io.Source.fromFile(args(0))
    val benchmarkList =
      try source.mkString.split("\n").toSeq finally source.close()
    val timelimit = if (args.length == 2)
      try args(1).toInt catch {case _: Throwable => usage(); 0}
    // 0 is at the end of the catch block in order to make the block's
    // type be Int so it lines up with the type of the try block.
    else 30 // the default time limit of 30 seconds

    val sfRoot = "/mnt/storage/sf110"

    val sfResultsRoot = "/mnt/storage/sf110-results"
    // adjust the range below if the number/setup of worker nodes
    // changes
    val loc = (2 to 4).toSeq.map{ i =>
      s"node-$i.multinode.jpf-doop.emulab.net"
    }.foldLeft(Map[String, String]()){ (m, w) =>
      m + (w -> sfResultsRoot)
    }
    initResDirs(loc)

    val conf = new SparkConf().setAppName("JDoop Executor")
    val sc = new SparkContext(conf)

    val distBenchmarks = sc.parallelize(
      benchmarkList map { b => Task(
        SF110Project(b),
        b, timelimit,
        Seq(sfRoot, b).mkString("/"),
        Seq(sfResultsRoot, b).mkString("/"))
      },
      benchmarkList.length // the number of partitions of the data
    )

    val r = distBenchmarks.map{runSF100JDoopTask}.reduce{(_, _) => ()}
    println(r) // I'm not sure if this is needed, but in case it is,
               // it will enforce 'r' to be evaluated.
    sc.stop()

    pullResults(loc, sfResultsRoot)
  }
}
