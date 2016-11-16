#!/usr/bin/env scala

// The script assumes a Debian-based operating system


import sys.process._

object InstallNFSClient {

  // converts an Int into an Option around Unit representing a process
  // success or failure
  implicit def liftRV(rv: Int): Option[Unit] = rv match {
    case 0 => Some(())
    case _ => None
  }

  def main(args: Array[String]): Unit = {
    if (args.length != 3) {
      println("Usage: <script-name> <nfs-server> <server-dir> <mount-dir>")
      println("  <nfs-server> is the NFS server name")
      println("  <server-dir> is the directory on the NFS server")
      println("  <mount-dir> is the directory to mount to the NFS")
      sys.exit(1)
    }

    val nfsServer = args(0)
    val serverDir = args(1)
    val mountDir  = args(2)

    for {
      _ <- s"sudo mkdir -p $mountDir".!
      _ <- s"sudo chown marko: $mountDir".!
      _ <-  "sudo apt-get install --yes nfs-common".!
      _ <- s"sudo mount $nfsServer:$serverDir $mountDir".!
    } yield ()
  }
}
