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


import java.io.File
import javax.xml.parsers.SAXParser
import scala.xml.{Elem, Node, NodeSeq}
import scala.xml.factory.XMLLoader

object Stats {

  case class SF110Project(projectDir: String)

  trait CovType

  case object BranchCov extends CovType {
    override def toString = "BRANCH"
  }
  case object InstructionCov extends CovType {
    override def toString = "INSTRUCTION"
  }

  val bothCovTypes = Set[CovType](InstructionCov, BranchCov)

  case class CovMetric(covered: Seq[Int], total: Int) {
    def +(that: CovMetric): CovMetric = {
      require(total == that.total)

      CovMetric(covered ++ that.covered, total)
    }

    lazy val coveredAvg: Double = covered.sum.toDouble / covered.length
    lazy val ratioAvg: Double = if (total == 0) 0.0 else coveredAvg / total
    lazy val percentageAvg: Double = 100.0 * ratioAvg
    lazy val stdDev: Double = Math.sqrt(
      covered.map{ c => (c - coveredAvg) * (c - coveredAvg) }.sum /
        covered.length
    )

    override def toString: String = Seq(
      "%.1f".format(coveredAvg),
      "±",
      "%.1f".format(stdDev),
      "/",
      total,
      "(" + "%2.1f".format(percentageAvg) + "%)"
    ) mkString(" ")
  }

  object CovMetric {
    def apply(covered: Int, total: Int): CovMetric =
      new CovMetric(Seq(covered), total)
  }

  type Time = Int

  case class BenchmarkStats(
    proj: SF110Project,
    branchCov: CovMetric,
    instructionCov: CovMetric,
    timelimit: Time
  ) {
    def +(that: BenchmarkStats): BenchmarkStats = {
      require(proj == that.proj && timelimit == that.timelimit)

      BenchmarkStats(
        proj,
        branchCov + that.branchCov,
        instructionCov + that.instructionCov,
        timelimit
      )
    }

    override def toString: String = Seq(
      proj.projectDir + ":",
      "branch", "=", branchCov + ",",
      "instruction", "=", instructionCov + ",",
      "timelimit", "=", timelimit.toString, "s"
    ).mkString(" ")
  }

  def processStats(stats: Seq[BenchmarkStats]): Map[Time, Set[BenchmarkStats]] = {
    val groupedByTimelimit: Map[Time, Seq[BenchmarkStats]] =
      stats.groupBy(_.timelimit)
    groupedByTimelimit map { case (t, seq) =>
      val groupedByBenchmark: Map[SF110Project, Seq[BenchmarkStats]] =
        seq.groupBy(_.proj)
      val aggregatedStats: Set[BenchmarkStats] = groupedByBenchmark.map {
        case (b, sameBenchmarkStats) =>
          b -> sameBenchmarkStats.reduce{_ + _}
      }.values.toSet
      t -> aggregatedStats
    }
  }

  def isBenchmarkDir(name: String): Boolean =
    try {
      val a = name.split("_")
      a(0).toInt
      a.length == 2
    } catch { case _: Throwable => false }

  def benchmarksInDir(root: File): Set[SF110Project] = {
    require(root.isDirectory)
    
    val set =
      root.listFiles.filter{ f =>
        f.isDirectory && isBenchmarkDir(f.getName)
      }.toSet
    set.map(d => SF110Project(d.getName))
  }

  // This is needed because JaCoCo reports point to a non-existing DTD
  // specification
  object MyXML extends XMLLoader[Elem] {
    override def parser: SAXParser = {
      val f = javax.xml.parsers.SAXParserFactory.newInstance
      f.setNamespaceAware(false)
      f.setFeature(
        "http://apache.org/xml/features/nonvalidating/load-external-dtd",
        false);
      f.newSAXParser
    }
  }

  def jacocoReport(f: File): Option[Elem] =
    try Some(MyXML.loadFile(f)) catch { case _: Throwable => None }

  def findCovMetric(e: Elem)(covType: CovType): Option[Node] = {
    val counters: NodeSeq = e \ "counter"
    counters.find(c => (c \ "@type").toString == covType.toString)
  }

  def isCounter(n: Node): Boolean = n match {
    case <counter/> => true
    case _ => false
  }

  def nodeAttr(node: Node)(attr: String): String = (node \ s"@$attr").text

  def counterToCovMetric(counter: Node): Option[CovMetric] =
    if (!isCounter(counter)) None
    else {
      val covered = nodeAttr(counter)("covered").toInt
      val missed  = nodeAttr(counter)("missed").toInt
      Some(CovMetric(covered, covered + missed))
    }

  def covFromFile(covTypes: Set[CovType])(f: File):
      Option[Map[CovType, CovMetric]] =
    for {
      e <- jacocoReport(f)
    } yield (
      for {
        t <- covTypes
        n <- findCovMetric(e)(t)
        m <- counterToCovMetric(n)
      } yield t -> m
    ).toMap
  
  def covForFiles(cov: Map[File, Set[CovType]]):
      Map[File, Option[Map[CovType, CovMetric]]] =
    cov.keySet.foldRight(Map[File, Option[Map[CovType, CovMetric]]]()){ (f, acc) =>
      acc + (f -> covFromFile(cov(f))(f))
    }

  def usage(): Unit = {
    println("Usage: <dir1 [dir2 ...]>")
    println("Every directory name is a natural number")
    sys.exit(1)
  }

  def extfromPathEnd(f: File, n: Integer): String =
    f.getPath.split("/").reverse(n)

  def extProjDir(f: File): String = extfromPathEnd(f, 2)
  def extTimelimit(f: File): Time = extfromPathEnd(f, 3).toInt

  def reportMap: Seq[String] => Map[File, Set[CovType]] = dirs =>
    (for {
      dir <- dirs
      bm  <- benchmarksInDir(new File(dir))
    } yield ((
      new File(
        Seq(dir, bm.projectDir, "jacoco-site", "report.xml")
          .mkString("/")
      ),
      bothCovTypes
    ))
    ).toMap

  def statsList:
      Map[File, Option[Map[CovType, CovMetric]]] => List[BenchmarkStats] =
    _.foldRight(List[BenchmarkStats]()){(kv, acc) =>
      val (f, oCovMap) = kv
      oCovMap match {
        case None => acc
        case Some(covMap) =>
          val bmstats = BenchmarkStats(
            proj = SF110Project(extProjDir(f)),
            branchCov = covMap.getOrElse(BranchCov, CovMetric(0, 0)),
            instructionCov = covMap.getOrElse(InstructionCov, CovMetric(0, 0)),
            timelimit = extTimelimit(f)
          )
          bmstats :: acc
      }
    }

  def run: Seq[String] => Map[Time, Set[BenchmarkStats]] =
    reportMap andThen covForFiles andThen statsList andThen processStats

  def main(args: Array[String]): Unit = {
    if (args.length == 0) usage()
    val dirs = args.toSeq
    try {
      dirs map { _.split("/").last.toInt }
      dirs foreach { d => if (!(new File(d).isDirectory)) usage() }
    }
    catch { case _: Throwable => usage() }

    val stats = run(dirs)
    stats foreach { case (t, set) =>
      println(s"Timelimit: $t")
      println("Results:")
      set
        .toSeq
        .sortBy(b => b.proj.projectDir.split("_")(0).toInt)
        .foreach{println}
    }
  }
}
