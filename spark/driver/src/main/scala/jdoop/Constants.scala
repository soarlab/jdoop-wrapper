package jdoop

object Constants {
  val totalCpuCores = 16
  val coresPerContainer = 4
  val memoryPerContainer = 8L * 1024 * 1024 * 1024 // 8 GB
  val cpuCoresFilePath = Seq(
    System.getProperty("java.io.tmpdir"),
    "cpu-cores"
  ).mkString("/")
  val defaultTimelimit = 30 // seconds
  val totalSparkClusterNodes = 4
  val workerMachines = (2 to totalSparkClusterNodes) map {
    i => s"node-$i.multinode.jpf-doop.emulab.net"
  } toSet
  val scratchResultsRoot = "/mnt/scratch/sf110-results"
  val finalResultsRoot = "/mnt/storage/sf110-results"
  val sfRoot = "/mnt/storage/sf110"
}
