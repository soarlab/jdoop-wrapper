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


import sys.process._

object Main {
  def readTime(filename: String): Int = {
    val source = scala.io.Source.fromFile(filename)
    try source.mkString.split("\n").toSeq(0).toInt finally source.close()
  }

  def main(args: Array[String]): Unit = {
    val fileList = s"find ${args(0)} -name total-time.txt".!!
      .split("\n").map{_.trim}.filter{!_.isEmpty}
    val times = fileList map readTime
    val paired = (fileList zip times).sortBy(_._2)
    paired foreach println
  }
}
