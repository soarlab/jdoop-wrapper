#!/usr/bin/env scala

// The script assumes a Debian-based operating system


import sys.process._

object InstallNFSServer {

  // converts an Int into an Option around Unit representing a process
  // success or failure
  implicit def liftRV(rv: Int): Option[Unit] = rv match {
    case 0 => Some(())
    case _ => None
  }

  def main(args: Array[String]): Unit = {
    if (args.length != 2) {
      println("Usage: <script-name> <N> <share-dir>")
      println("  <N> is the number of NFS clients")
      println("  <share-dir> is the directory to share over NFS")
      sys.exit(1)
    }

    val count = args(0).toInt
    val shareDir = args(1)

    val clients = (1 to count).map{i => "node-" + (i + 1)}.mkString(",")
    val exports = s"$shareDir $clients(rw,sync,no_root_squash)"

    for {
      _ <-  s"sudo mkdir -p $shareDir".!
      _ <-  s"sudo chown marko: $shareDir".!
      _ <-   "sudo apt-get install --yes nfs-kernel-server".!
      _ <- (s"echo $exports" #| "sudo tee /etc/exports" !)
      _ <-   "sudo service nfs-kernel-server restart".!
    } yield ()
  }
}
