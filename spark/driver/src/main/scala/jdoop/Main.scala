package jdoop

import org.apache.spark.{SparkConf, SparkContext}
import sys.process._

object Main {

  val testSP1 = SourcePackage(
    srcPkgName = "commons-beanutils",
    pkgDir = "commons-beanutils-1.9.3",
    srcDir = "src/main/java",
    binDir = "target/classes",
    fqdn   = "org.apache.commons.beanutils",
    buildsBinPkgs = Set(
      "libcommons-beanutils-java",
      "libcommons-beanutils-java-doc"
    )
  )

  val testSP2 = SourcePackage(
    srcPkgName = "commons-exec",
    pkgDir = "commons-exec-1.3",
    srcDir = "src/main/java",
    binDir = "target/classes",
    fqdn   = "org.apache.commons.exec",
    buildsBinPkgs = Set("libcommons-exec-java")
  )

  val containerNames = Seq("rob", "frog")

  val benchmarkResPaths = Seq(
    "/mnt/storage/to-share-over-nfs/commons-beanutils"
  )

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

  // defines a task that runs JDoop on a Debian source package
  def debSrcPkgJDoop(task: Task): Unit = {

    val baseContainerName = "jdoop"
    val benchmarkDir = "/benchmark"
    val lxcUser = "debian"
    val jdoopDir = s"/home/$lxcUser/jdoop"
    val dependencyDir = s"/home/$lxcUser/jdoop-project"
    val testsDir = "tests"
    // create a directory for results for this task on the worker node
    val successNewDir = new java.io.File(task.baseDir).mkdirs
    implicit val containerName: String = task.containerName

    // Command for downloading the source package's build dependencies
    val getBuildDepCmd = List(
      "apt-get",
      "build-dep",
      "--assume-yes",
      task.pkg.srcPkgName
    )

    // Command for getting the package, extracting it, and building it
    val getSrcPkgCmd = List(
      "su", "--login", "-c",
      Seq("cd",
        benchmarkDir,
        "&&",
        "apt-get",
        "source",
        "--compile",
        task.pkg.srcPkgName
      ).mkString(" "),
      lxcUser
    )
    // start JDoop on the benchmark
    val jdoopCmd = List(
      "su", "--login", "-c",
      Seq("cd",
        benchmarkDir,
        "&&",
        "python",
        s"$jdoopDir/jdoop.py",
        "--package-name", task.pkg.fqdn,
        "--root",
        Seq(benchmarkDir, task.pkg.pkgDir, task.pkg.srcDir).mkString("/"),
        "--timelimit", task.timelimit,
        "--jpf-core-path", s"$dependencyDir/jpf-core",
        "--jdart-path", s"$dependencyDir/jdart",
        "--sut-compilation",
        Seq(benchmarkDir, task.pkg.pkgDir, task.pkg.binDir).mkString("/"),
        "--test-compilation",
        Seq(benchmarkDir, testsDir).mkString("/"),
        "--junit-path", s"$jdoopDir/lib/junit4.jar",
        "--randoop-path", s"$jdoopDir/lib/randoop.jar",
        "--jacoco-path", s"$jdoopDir/lib/jacocoant.jar",
        "--generate-report"
      ).mkString(" "),
      lxcUser
    )

    for {
      // create an ephemeral container for jdoop with an overlay fs
      _ <- (s"sudo lxc-copy --ephemeral --name $baseContainerName " +
        s"--newname ${task.containerName} " +
        s"--mount bind=${task.baseDir}:$benchmarkDir").!
      // sleep for a few seconds to make sure a network device is
      // ready
      _ <-  "sleep 5s".!
      // TODO: Constrain container resources. This info should also be
      // part of Task. To put the limits, run:
      // $ lxc config set <container-name> limits.cpu 1 // limits it to any one core
      // $ lxc config set <container-name> limits.memory 4GB
      // $ lxc config set <container-name> limits.memory.swap false // disable swap because it's enabled by default
      _ <-  s"sudo lxc-info --name ${task.containerName} --state".!
      _ <- in_container(getBuildDepCmd)
      _ <- in_container(s"chown $lxcUser: $benchmarkDir")
      _ <- in_container(getSrcPkgCmd)
      _ <- in_container(jdoopCmd)
    } yield ()

    // Stop the container (this will also destroy it because it is
    // ephemeral). We are running this out of the for comprehension to
    // make sure the container is destroyed.
    (s"sudo lxc-stop --name ${task.containerName}").!
  }

  def main(args: Array[String]): Unit = {
    val t = Task(testSP1, "jTest", "/tmp/test", 10)
    debSrcPkgJDoop(t)
  }
}
