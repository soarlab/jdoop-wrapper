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


object Constants {
  val totalCpuCores = 16
  val coresPerContainer = 4
  val memoryPerContainer = 8L << 30 // 8 GB
  val cpuCoresFilePath = Seq(
    System.getProperty("java.io.tmpdir"),
    "cpu-cores"
  ).mkString("/")
  val totalSparkClusterNodes = 15
  val masterMachine = "node-1.multinode.jpf-doop.emulab.net"
  val workerMachines = (2 to totalSparkClusterNodes) map {
    i => s"node-$i.multinode.jpf-doop.emulab.net"
  } toSet
  val scratchResultsRoot = "/mnt/scratch/sf110-results"
  val finalResultsRoot = "/mnt/storage/sf110-results"
  val sfRoot = "/mnt/storage/sf110"

  // an environment variable that is passed in to the JVM:
  // https://bugs.openjdk.java.net/browse/JDK-4971166
  val javaToolOptions = Seq(
    s"-Xmx${(memoryPerContainer * 0.9).toLong}" // 10% less than a
                                                // container has
  ) mkString("JAVA_TOOL_OPTIONS=\"", " ", "\"")
}
