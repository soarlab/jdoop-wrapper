#!/usr/bin/env scala

// Usage: <script-name> <template> <release> <timestamp>
//   <template> is an LXC template
//   <release> is a Debian release
//   <timestamp> is a valid timestamp for the Debian Snapshot Archive

import sys.process._

object DebianContainer {
  // An assumed LXC template
  // val defaultTemplate = "/mnt/storage/jdoop-wrapper/lxc/templates/lxc-debian"
  val defaultTemplate = ("pwd" !!).trim + "/templates/lxc-debian"
  // A Debian release
  val defaultDebianRelease = "stretch"
  // A valid time stamp for the Debian Snapshot Archive
  val defaultTimestamp = "20161022T104905Z"

  def main(args: Array[String]): Unit = {
    val template = if (args.length > 0) args(0) else defaultTemplate
    val debianRelease = if (args.length > 1) args(1) else defaultDebianRelease
    val timestamp = if (args.length > 2) args(2) else defaultTimestamp
    Process(List("sudo",
      "lxc-create",
      "--template", template,
      "--name", debianRelease,
      "--",
      "--release", debianRelease,
      "--arch", "amd64",
      "--mirror=" +
        s"http://snapshot.debian.org/archive/debian/$timestamp/",
      "--security-mirror=" +
        s"http://snapshot.debian.org/archive/debian-security/$timestamp/")) !
  }
}
