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
// along with maline.  If not, see <http://www.gnu.org/licenses/>.


import sys.process._

object Containers {

  def getNodes(): Set[String] = {
    val scriptPath = System.getenv("SCRIPT_PATH")
    val scriptDir  = scriptPath.substring(0, scriptPath.lastIndexOf("/"))

    val lines = s"cat $scriptDir/../spark/conf/slaves".!!
      .split("\n")
      .map{_.trim}
      .filter{ _.startsWith("node-") }
      .toSet

    lines + "localhost"
  }

  def stopContainers(host: String): Unit = {
    val containers = s"ssh $host sudo lxc-ls --active -1".!!
      .split("\n")
      .map{_.trim}
      .filter{!_.isEmpty}
      .toSeq
    containers foreach { c =>
      s"ssh $host sudo lxc-destroy --force --name $c".!
    }
  }

  def main(args: Array[String]): Unit = {
    getNodes() foreach stopContainers
  }
}
