#!/bin/sh
export SCRIPT_PATH=$(readlink -f "$0")
exec scala "$0" "$@"
!#

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


import sys.process._

object CleanUpWorkDirs {

  def getNodes(): Set[String] = {
    val scriptPath = System.getenv("SCRIPT_PATH")
    val scriptDir  = scriptPath.substring(0, scriptPath.lastIndexOf("/"))

    s"cat $scriptDir/conf/slaves".!!
      .split("\n")
      .map{_.trim}
      .filter{ _.startsWith("node-") }
      .toSet
  }

  def delWorkDirCont(host: String): Unit =
    s"ssh $host rm -rf /mnt/storage/spark/spark-2.1.0-bin-hadoop2.7/work/*".!

  def main(args: Array[String]): Unit =
    getNodes() foreach delWorkDirCont
}
