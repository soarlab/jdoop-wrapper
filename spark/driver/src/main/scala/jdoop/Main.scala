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
import java.io.File
import org.apache.spark.{SparkConf, SparkContext}
import org.apache.spark.rdd.RDD
import sys.process._


object Main {

  case class Env(
    tool: Tool,
    benchmarkList: Seq[String],
    timelimit: Int,
    fullScratchResultsDir: String,
    loc: Map[String, String],
    resultsSuffixDir: String
  )

  def mkFilePath(xs: String*): String = new File(xs.mkString("/")).getPath

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
    println("Two arguments needed: <list-of-benchmarks.txt> " +
      "<jdoop|randoop|evosuite>")
    println("Optional argument: <time limit> in seconds")
    println("Optional argument: <name> for the experiment name")
    sys.exit(1)
  }

  def unsafePrepareEnv(args: IndexedSeq[String]): Env = {
    if (args.length < 1 || args.length > 4)
      usage()

    val source = scala.io.Source.fromFile(args(0))
    val benchmarkList =
      try source.mkString.split("\n").toSeq finally source.close()

    val tool = args(1).toLowerCase match {
      case "jdoop"    => JDoop
      case "randoop"  => Randoop
      case "evosuite" => EvoSuite
      case _          => usage(); JDoop // just to make the type checker happy
    }

    val timelimit =
      if (args.length >= 3)
        try args(2).toInt catch {case _: Throwable => usage(); 0}
        // 0 is at the end of the catch block in order to make the
        // block's type be Int so it lines up with the type of the try
        // block.
      else defaultTimelimit // the default time limit of 30 seconds
    val experimentName =
      if (args.length >= 4)
        args(3)
      else
        new java.text.SimpleDateFormat("yyyy-MM-DD-HH-mm-ss").format(
          new java.util.Date())
    val resultsSuffixDir = mkFilePath(
      experimentName,
      tool.toString.toLowerCase,
      timelimit.toString
    )
    val fullScratchResultsDir = mkFilePath(scratchResultsRoot, resultsSuffixDir)

    val loc = workerMachines.foldLeft(Map[String, String]()){
      (map, machine) => map +
      (machine -> fullScratchResultsDir)
    }
    val failed = initResDirs(
      loc + ("localhost" -> fullScratchResultsDir),
      benchmarkList
    )
    if (failed) {
      println("Remove existing colliding benchmark directories first!")
      sys.exit(1)
    }

    pushCgroupsFile(loc.keySet, cpuCoresFilePath)

    Env(
      tool,
      benchmarkList,
      timelimit,
      fullScratchResultsDir,
      loc,
      resultsSuffixDir
    )
  }

  def main(args: Array[String]): Unit = {

    val env = unsafePrepareEnv(args)
    import env._

    val conf = new SparkConf().setAppName(s"${tool.toString} Executor")
    val sc = new SparkContext(conf)

    val distBenchmarks = parallelizeBenchmarks(
      benchmarkList,
      sc,
      timelimit,
      sfRoot,
      fullScratchResultsDir
    )

    val r = distBenchmarks.map{RunTask(tool)}.reduce{(_, _) => ()}
    sc.stop()

    pullResults(
      loc + ("localhost" -> fullScratchResultsDir),
      mkFilePath(finalResultsRoot, resultsSuffixDir)
    )

    println("Results are available in: " +
      mkFilePath(finalResultsRoot, resultsSuffixDir))
  }
}
