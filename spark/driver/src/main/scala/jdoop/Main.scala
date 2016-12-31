package jdoop

import org.apache.spark.{SparkConf, SparkContext}

object Main {

  def runSF100JDoopTask(task: SF110Task): Unit = {
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
    val tmpWorkDir = Files.createTempDirectory("work-").toString
    val workDir = "/work"
    val lxcUser = "debian"
    val jdoopDir = s"/home/$lxcUser/jdoop"
    val jDoopDependencyDir = s"/home/$lxcUser/jdoop-project"
    val testsDir = "tests"
    val relativeSrcDir = "src/src/main/java"
    val relativeBinDir = "bin"
    val dependencyLibs = recursiveListFiles(
      new File(task.masterNodeBenchmarkDir + s"/$relativeBinDir/lib"),
      """.*\.jar""".r)
      .mkString(":")
      .replaceAll(task.masterNodeBenchmarkDir, benchmarkDir)

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

    var r: Int = s"sudo chown 1000:1000 $tmpWorkDir".!
      if (r != 0) println(s"${task.containerName}: sudo chown 1000:1000 $tmpWorkDir didn't return 0")
    r = (s"sudo lxc-copy --ephemeral --name $baseContainerName " +
      s"--newname ${task.containerName} " +
      s"--mount bind=${task.masterNodeBenchmarkDir}:$benchmarkDir:ro," +
      s"bind=$tmpWorkDir:$workDir").!
    if (r != 0) println(s"${task.containerName}: container creation didn't return 0")
    r = "sleep 5s".!
    if (r != 0) println(s"${task.containerName}: sleep 5 didn't return 0")
    r = s"sudo lxc-info --name ${task.containerName} --state".!
    if (r != 0) println(s"${task.containerName}: lxc-info didn't return 0")
    r = in_containerSeq(jdoopCmd)
    if (r != 0) println(s"${task.containerName}: running JDoop in the container didn't return 0")

    // for {
    //   _ <- s"sudo chown 1000:1000 $tmpWorkDir".!
    //   // create an ephemeral container for jdoop with an overlay fs
    //   _ <- (s"sudo lxc-copy --ephemeral --name $baseContainerName " +
    //     s"--newname ${task.containerName} " +
    //     s"--mount bind=${task.masterNodeBenchmarkDir}:$benchmarkDir:ro," +
    //     s"bind=$tmpWorkDir:$workDir").!
    //   // sleep for a few seconds to make sure a network device is
    //   // ready
    //   _ <- "sleep 5s".!
    //   // TODO: Constrain container resources. This info should also be
    //   // part of Task. It's not going to work with LXD as it doesn't
    //   // see the regular LXC image pool.
    //   _ <- s"sudo lxc-info --name ${task.containerName} --state".!
    //   // _ <- in_container(s"chown $lxcUser:$lxcUser $workDir")
    //   _ <- in_containerSeq(jdoopCmd)
    // } yield ()

    // Stop the container (this will also destroy it because it is
    // ephemeral). We are running this outside the for comprehension
    // to make sure the container is destroyed.
    s"sudo lxc-stop --name ${task.containerName}".!

    s"sudo chown -R ${System.getenv("USER")}: $tmpWorkDir".!
    s"rsync -a $tmpWorkDir/ ${task.masterNodeWorkDir}/".!
    // s"rm -rf $tmpWorkDir".!

    // "sleep 1s".!
    // "hostname".!! + task.project.projectDir
  }

  def usage(): Unit = {
    println("One argument needed: <list-of-benchmarks.txt>")
    println("Optional argument: <time limit> in seconds")
    sys.exit(1)
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
    val nfsShare = "/mnt/storage/to-share-over-nfs/test"

    // create all benchmark directories in advance on the master node
    // because worker nodes don't have permission to change ownership
    // of directories in an NFS file system, yet I need to set up the
    // ownership for the container to work.
    benchmarkList foreach { b =>
      val workDir = Seq(nfsShare, b).mkString("/")
      s"mkdir -p $workDir".!
      // set the owner to this user and group, otherwise the
      // container won't be able to write to this directory
      s"sudo chmod o+rwx $workDir".!
    }

    val conf = new SparkConf().setAppName("JDoop Executor")
    val sc = new SparkContext(conf)

    val distBenchmarks = sc.parallelize(
      benchmarkList map { b => SF110Task(
        SF110Project(b),
        b, timelimit,
        Seq(sfRoot, b).mkString("/"),
        Seq(nfsShare, b).mkString("/"))
      } // drop(1) take(1)
    )

    val r = distBenchmarks.map{runSF100JDoopTask}.reduce{(_, _) => ()}

    println(r)

    // val list: String = distBenchmarks.map{runSF100JDoopTask}.reduce(_ + "\n" + _)
    // val list: String = distBenchmarks.map{runSF100JDoopTask}.reduce((
    //   a: String, b: String) => a + b.toList.mkString("").replaceAll("\n", ""))
    // println(list)
    // list foreach { a => println(a) }

    // sc.stop()
  }
}
