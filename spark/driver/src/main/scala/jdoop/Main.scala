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
import java.io.File
import org.apache.spark.{SparkConf, SparkContext}
import org.apache.spark.rdd.RDD
import sys.process._


object Main {

  case class Env(
    benchmarks: Seq[Task],
    tools: Set[Tool],
    hostWorkDirs: Set[File]
  )

  def mkFilePath(xs: String*): String = new File(xs.mkString("/")).getPath

  /**
    * Creates result directories if they don't exist.
    *
    * @param machines A set of machines where the directories should be created
    * @param dirs A set of directorires to create
    */
  def initResDirs(machines: Set[String], dirs: Set[File]): Unit = {
    val mkdirCmd = dirs map { d => s"mkdir -p ${d.getPath}" } mkString(" && ")
    machines foreach { m => s"ssh $m $mkdirCmd".! }
    mkdirCmd.replaceAll(scratchResultsRoot, finalResultsRoot).!
  }

  def pushCgroupsFile(machines: Set[String], path: String): Unit = {
    CPUCoresUtil.generateFile()
    machines foreach { m => s"rsync -a $path $m:$path".! }
  }

  def usage(): Unit = {
    println("At least two arguments needed: <timelimit> <task-list...>")
    println("  task-list = <jdoop|randoop>,<experiment-name>," +
      "<repetitions>,<input-list>")
    sys.exit(1)
  }

  def unsafeProcessInputArg(arg: String)(
    timelimit: Int,
    defaultExpName: String): Seq[Task] = {

    val s = arg.split(",")
    val (toolStr, experimentName, repStr, inputFile) = (s(0), s(1), s(2), s(3))
    val tool = toolStr.toLowerCase match {
      case "randoop"  => Randoop
      case _          => JDoop // assume JDoop as default
    }
    val finalExperimentName =
      if (experimentName.isEmpty) defaultExpName
      else experimentName
    val repetitions = try repStr.toInt catch { case _: Throwable => 1 }

    val source = scala.io.Source.fromFile(inputFile)
    val benchmarkList =
      try source.mkString.split("\n").toSeq finally source.close()
    val multipliedList = ((0 until repetitions) foldRight Seq[(String, Int)]()){
      (r, acc) => acc ++ (benchmarkList zip List.fill(benchmarkList.length)(r))
    }

    multipliedList.map{ case ((benchmark, repIndex)) =>
      val resultsSuffixDir = mkFilePath(
        finalExperimentName,
        tool.toString.toLowerCase,
        repIndex.toString,
        timelimit.toString
      )
      val masterNodeDir = new File(mkFilePath(
        finalResultsRoot, resultsSuffixDir))
      Task(
        project = SF110Project(benchmark),
        containerName =
          s"$benchmark-${tool.toString.toLowerCase}-" +
            s"$finalExperimentName-${repIndex.toString}",
        timelimit = timelimit,
        hostBenchmarkDir = mkFilePath(sfRoot, benchmark),
        hostWorkDir = mkFilePath(
          scratchResultsRoot,
          resultsSuffixDir,
          benchmark
        ),
        masterNodeDir = masterNodeDir,
        tool = tool
      )
    }
  }

  def unsafePrepareEnv(args: IndexedSeq[String]): Env = {
    if (args.length < 2) usage()

    val timelimit =
      try args(0).toInt catch {case _: Throwable => usage(); 0}
      // 0 is at the end of the catch block in order to make the
      // block's type be Int so it lines up with the type of the try
      // block.
    val defaultExperimentName =
      new java.text.SimpleDateFormat("yyyy-MM-DD-HH-mm-ss").format(
        new java.util.Date())

    val benchmarks: Seq[Task] = (args drop(1) foldRight Seq[Task]()) {
      (arg, accBenchmarks) =>
      accBenchmarks ++
        unsafeProcessInputArg(arg)(timelimit, defaultExperimentName)
    }
    val benchmarksSorted = benchmarks.sorted

    val hostWorkDirs = benchmarks.map{ b => new File(b.hostWorkDir) }.toSet
    initResDirs(workerMachines, hostWorkDirs)
    pushCgroupsFile(workerMachines, cpuCoresFilePath)
    val tools = benchmarks map { _.tool } toSet

    Env(benchmarksSorted, tools, hostWorkDirs)
  }

  def main(args: Array[String]): Unit = {
    val env = unsafePrepareEnv(args)
    import env._

    val conf = new SparkConf().setAppName(s"${tools.mkString("-")} Executor")
    val sc = new SparkContext(conf)

    val distBenchmarks = sc.parallelize(benchmarks, benchmarks.length)
    distBenchmarks.map{RunTask(_)}.reduce{(_, _) => ()}
    sc.stop()

    println("Results are available in:")
    hostWorkDirs.map{ _.getPath.replace(scratchResultsRoot, finalResultsRoot)
      .split("/").dropRight(1).mkString("/")
    }.toSeq.sorted.foreach{d => println(s"  $d")}
  }
}
