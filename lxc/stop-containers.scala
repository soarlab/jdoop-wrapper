#!/bin/sh
export SCRIPT_PATH=$(readlink -f "$0")
exec scala "$0" "$@"
!#

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
    containers foreach { c => s"ssh $host sudo lxc-stop --name $c".! }
  }

  def main(args: Array[String]): Unit = {
    getNodes() foreach stopContainers
  }
}
