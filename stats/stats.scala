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
// along with jdoop-wrapper.  If not, see <http://www.gnu.org/licenses/>.


import java.io.File
import javax.xml.parsers.SAXParser
import scala.xml.{Elem, Node, NodeSeq}
import scala.xml.factory.XMLLoader


object Stats {

  case class SF110Project(projectDir: String)

  sealed trait CovType

  case object BranchCov extends CovType {
    override def toString = "BRANCH"
  }
  case object InstructionCov extends CovType {
    override def toString = "INSTRUCTION"
  }
  case object CyclomaticComplexity extends CovType {
    override def toString = "COMPLEXITY"
  }
  case object FailedTestCaseCount extends CovType {
    override def toString = "FTCCOUNT"
  }
  case object RandoopTestCaseCount extends CovType {
    override def toString = "RTCCOUNT"
  }
  case object JDartTestCaseCount extends CovType {
    override def toString = "JTCCOUNT"
  }
  case object TotalTestCaseCount extends CovType {
    override def toString = "TCCOUNT"
  }
  // The method and class coverage types are used only to obtain the
  // total number of methods and classes in a benchmark
  case object MethodCov extends CovType {
    override def toString = "METHOD"
  }
  case object ClassCov extends CovType {
    override def toString = "CLASS"
  }

  val allMeasuredCovTypes = Set[CovType](
    InstructionCov,
    BranchCov,
    CyclomaticComplexity,
    FailedTestCaseCount,
    RandoopTestCaseCount,
    JDartTestCaseCount,
    TotalTestCaseCount
  )

  val overviewCovTypes = Set[CovType](
    InstructionCov,
    BranchCov,
    MethodCov,
    ClassCov,
    CyclomaticComplexity
  )

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
    lazy val ratioStdDev: Double =
      if (total == 0 || covered.sum == 0) 0.0 else stdDev / coveredAvg
    lazy val percentageStdDev: Double = 100.0 * ratioStdDev

    def toStringSub: String = Seq(
      "%.1f".format(coveredAvg),
      "±",
      "%.1f".format(stdDev)
    ) mkString(" ")

    def toPercentage: String = "(" + "%2.1f".format(percentageAvg) + "%)"

    override def toString: String = Seq(
      toStringSub,
      "/",
      total,
      toPercentage
    ) mkString(" ")
  }

  object CovMetric {
    def apply(covered: Int, total: Int): CovMetric =
      new CovMetric(Seq(covered), total)
  }

  def compareSF110Projects(a: SF110Project, b: SF110Project): Int = {
    val intPrefix: SF110Project => Int = _.projectDir.split("_")(0).toInt
    val aPref = intPrefix(a)
    val bPref = intPrefix(b)
    if (aPref < bPref)      -1
    else if (aPref > bPref)  1
    else                     0
  }

  type Time = Int

  case class BenchmarkStats(
    proj:                 SF110Project,
    branchCov:            CovMetric,
    instructionCov:       CovMetric,
    cyclomaticCxty:       CovMetric,
    failedTestCaseCount:  CovMetric,
    randoopTestCaseCount: CovMetric,
    jdartTestCaseCount:   CovMetric,
    testCaseCount:        CovMetric,
    timelimit:            Time
  ) extends Ordered[BenchmarkStats] {
    def +(that: BenchmarkStats): BenchmarkStats = {
      require(proj == that.proj && timelimit == that.timelimit)

      BenchmarkStats(
        proj,
        branchCov + that.branchCov,
        instructionCov + that.instructionCov,
        cyclomaticCxty + that.cyclomaticCxty,
        failedTestCaseCount + that.failedTestCaseCount,
        randoopTestCaseCount + that.randoopTestCaseCount,
        jdartTestCaseCount + that.jdartTestCaseCount,
        testCaseCount + that.testCaseCount,
        timelimit
      )
    }

    def compare(that: BenchmarkStats): Int =
      compareSF110Projects(this.proj, that.proj)

    override def toString: String = Seq(
      proj.projectDir + ":",
      "brnch",     "=", branchCov + ",",
      "instr",     "=", instructionCov + ",",
      "cxty",      "=", cyclomaticCxty + ",",
      "# f tests", "=", failedTestCaseCount.toStringSub,
      "# r tests", "=", randoopTestCaseCount.toStringSub,
      "# j tests", "=", jdartTestCaseCount.toStringSub,
      "# tests",   "=", testCaseCount.toStringSub
    ).mkString(" ")

    def toCSV: String = Seq(
      proj.projectDir,
      branchCov,
      instructionCov,
      cyclomaticCxty,
      failedTestCaseCount.toStringSub,
      randoopTestCaseCount.toStringSub,
      jdartTestCaseCount.toStringSub,
      testCaseCount.toStringSub,
      timelimit.toString
    ).mkString("\t")

    def toCSVsub: String = {
      val fmt: Double => String = x => "%.1f".format(x)

      def avgNDevPerct(cm: CovMetric): Seq[String] = Seq(
        cm.coveredAvg,
        cm.percentageAvg,
        cm.stdDev,
        cm.percentageStdDev
      ) map fmt

      def avgNDev(cm: CovMetric): Seq[String] = Seq(
        cm.coveredAvg,
        cm.stdDev
      ) map fmt

      (Seq(proj.projectDir) ++
        Seq(
          branchCov,
          instructionCov,
          cyclomaticCxty
        ).map{avgNDevPerct}.flatten ++
        Seq(
          failedTestCaseCount,
          randoopTestCaseCount,
          jdartTestCaseCount,
          testCaseCount
        ).map{avgNDev}.flatten ++
        Seq(timelimit.toString)
      ) mkString("\t")
    }
  }

  case class BenchmarkInfo(
    proj:           SF110Project,
    branchCov:      CovMetric,
    instructionCov: CovMetric,
    methodCov:      CovMetric,
    classCov:       CovMetric,
    cyclomaticCxty: CovMetric
  ) extends Ordered[BenchmarkInfo] {
    def compare(that: BenchmarkInfo): Int =
      compareSF110Projects(this.proj, that.proj)

    override def toString: String = Seq(
      proj.projectDir + ":",
      "brnch", "=", branchCov.total + ",",
      "instr", "=", instructionCov.total + ",",
      "mthd",  "=", methodCov.total + ",",
      "class", "=", classCov.total + ",",
      "cxty",  "=", cyclomaticCxty.total.toString
    ).mkString(" ")

    def toCSV: String = (proj.projectDir +:
      Seq(
        branchCov,
        instructionCov,
        methodCov,
        classCov,
        cyclomaticCxty
      ).map{_.total.toString}
    ).mkString("\t")
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

  def tcCountFromFile(f: File): Int = {
    val source = scala.io.Source.fromFile(f)
    try source.mkString.split("\n").toSeq(0).toInt finally source.close()
  }

  def jacocoReport(f: File): Option[Elem] = {
    /**
      * Extends coverage information from a JaCoCo report with the
      * number of failed, randoop, jdart, and total test cases
      * executed.
      */
    def extendedReport: Elem = {

      def countToXMLCounter(cType: CovType, countFileName: String): Node = {
        val c = tcCountFromFile(new File(
          (f.getPath.split("/").dropRight(2) ++ Seq(countFileName))
            .mkString("/"))
        )
        <counter type={cType.toString} missed="0" covered={c.toString}/>: Node
      }

      val report = MyXML.loadFile(f)
      val counters = report \ "counter"
      val testCaseCountTypeMap = Map[CovType, String](
        FailedTestCaseCount  -> "failed-test-case-count.txt",
        RandoopTestCaseCount -> "randoop-test-case-count.txt",
        JDartTestCaseCount   -> "jdart-test-case-count.txt",
        TotalTestCaseCount   -> "test-case-count.txt"
      )

      val testCaseCounts = for {
        kv <- testCaseCountTypeMap
      } yield countToXMLCounter(kv._1, kv._2)

      <report>{counters ++ testCaseCounts.toSeq}</report>
    }

    try Some(extendedReport) catch { case _: Throwable => None }
  }

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

  def reportMap(dirs: Seq[String], covTypes: Set[CovType]):
      Map[File, Set[CovType]] =
    (for {
      dir <- dirs
      bm  <- benchmarksInDir(new File(dir))
    } yield ((
      new File(
        Seq(dir, bm.projectDir, "jacoco-site", "report.xml")
          .mkString("/")
      ),
      covTypes
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
            cyclomaticCxty = covMap.getOrElse(
              CyclomaticComplexity, CovMetric(0, 0)
            ),
            failedTestCaseCount = covMap.getOrElse(
              FailedTestCaseCount,
              CovMetric(0, 0)
            ).copy(total = 1), // needed so we can add up multiple executions
            randoopTestCaseCount = covMap.getOrElse(
              RandoopTestCaseCount,
              CovMetric(0, 0)
            ).copy(total = 1), // needed so we can add up multiple executions
            jdartTestCaseCount = covMap.getOrElse(
              JDartTestCaseCount,
              CovMetric(0, 0)
            ).copy(total = 1), // needed so we can add up multiple executions
            testCaseCount = covMap.getOrElse(
              TotalTestCaseCount,
              CovMetric(0, 0)
            ).copy(total = 1), // needed so we can add up multiple executions
            timelimit = extTimelimit(f)
          )
          bmstats :: acc
      }
    }

  def run: Seq[String] => Map[Time, Set[BenchmarkStats]] = dirs => {
    val rMap = reportMap(dirs, allMeasuredCovTypes)
    val covForFilesMap = covForFiles(rMap)
    val bmStatsList = statsList(covForFilesMap)
    processStats(bmStatsList)
  }

  def getBenchmarkInfo: Seq[String] => Set[BenchmarkInfo] = dirs => {
    def zeroedCovMetric(map: Map[CovType, CovMetric], ct: CovType): CovMetric =
      map.getOrElse(ct, CovMetric(0, 0)).copy(covered = Seq(0))

    val rMap = reportMap(dirs, overviewCovTypes)
    val covForFilesMap: Map[File, Option[Map[CovType, CovMetric]]] =
      covForFiles(rMap)
    covForFilesMap.foldRight(Set.empty[BenchmarkInfo]){(kv, accSet) =>
      val (f, oCovMap) = kv
      accSet ++ (oCovMap match {
        case Some(covMap) =>
          Set(BenchmarkInfo(
            proj           = SF110Project(extProjDir(f)),
            branchCov      = zeroedCovMetric(covMap, BranchCov),
            instructionCov = zeroedCovMetric(covMap, InstructionCov),
            methodCov      = zeroedCovMetric(covMap, MethodCov),
            classCov       = zeroedCovMetric(covMap, ClassCov),
            cyclomaticCxty = zeroedCovMetric(covMap, CyclomaticComplexity)
          ))
        case None => Set()
      })
    }
  }

  def main(args: Array[String]): Unit = {
    val flags = Map(
      // "csv"    -> "--csv",
      "csvSub" -> "--csvsub",
      "total"  -> "--total"
    )
    if (args.length == 0) usage()

    // val csvFlag    = args(0) == flags("csv")
    val csvSubFlag = args(0) == flags("csvSub")
    val totalFlag  = args(0) == flags("total")
    val dirs       = args
      .drop(if (flags.values.toSeq.contains(args(0))) 1 else 0)
      .toSeq
    try {
      dirs map { _.split("/").last.toInt }
      dirs foreach { d => if (!(new File(d).isDirectory)) usage() }
    }
    catch { case _: Throwable => usage() }

    if (totalFlag) {
      val bmInfoStats = getBenchmarkInfo(dirs)
      // Print the header first
      println("benchmark\tbranches\tinstructions\tmethods\tclasses\tcyclomatic")
      bmInfoStats.toSeq.sorted.foreach{b => println(b.toCSV)}
    }
    else {
      val stats = run(dirs)

      if (!csvSubFlag) {
        stats foreach { case (t, set) =>
          println(s"Timelimit: $t")
          println("Results:")
          set.toSeq.sorted.foreach{println}
        }
      } else {
        // Print the header first
        println("benchmark" +
          "\tbranch\tbranch_p" +
          "\tbranch_sd\tbranch_sdp" +
          "\tinstruction\tinstruction_p" +
          "\tinstruction_sd\tinstruction_sdp" +
          "\tcyclomatic\tcyclomatic_p" +
          "\tcyclomatic_sd\tcyclomatic_sdp" +
          "\tftests\tftests_sd" +
          "\trtests\trtests_sd" +
          "\tjtests\tjtests_sd" +
          "\ttests\ttests_sd" +
          "\ttimelimit")
        val statsToString: BenchmarkStats => String = _.toCSVsub
          // if (csvFlag) _.toCSV
          // else         _.toCSVsub
        stats foreach { case (_, set) =>
          set.toSeq.sorted.foreach{b => println(statsToString(b))}
        }
      }
    }
  }
}
