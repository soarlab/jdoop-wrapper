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
import sys.process._

object Main {

  val jacocoDir = "jacoco-site"

  def isBenchmarkDir(name: String): Boolean =
    try {
      val a = name.split("_")
      a(0).toInt
      a.length == 2
    } catch { case _: Throwable => false }

  def findMissing(dir: File): Set[String] = {
    val allBenchmarks = s"ls -1 ${dir.getPath}".!!
      .split("\n")
      .toSet[String]
      .map{_.trim}
      .filter{!_.isEmpty}
      .filter{isBenchmarkDir}

    val successfulBenchmarks = s"find ${dir.getPath} -name $jacocoDir".!!
      .split("\n")
      .toSet[String]
      .map{_.trim}
      .filter{!_.isEmpty}
      .map{_.split("/").dropRight(1).last}
      .filter{isBenchmarkDir}

    allBenchmarks.toSet -- successfulBenchmarks.toSet
  }

  def main(args: Array[String]): Unit = {
    args foreach {dir =>
      val missing = findMissing(new File(dir))
      println(s"---- $dir ----")
      missing.toSeq.sortBy(_.split("_")(0).toInt).foreach{println}
    }
  }
}
