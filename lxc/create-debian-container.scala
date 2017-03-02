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


// Usage: <script-name> <template> <release> <timestamp>
//   <template> is an LXC template
//   <release> is a Debian release
//   <timestamp> is a valid timestamp for the Debian Snapshot Archive

import sys.process._

object DebianContainer {
  val scriptPath = System.getenv("SCRIPT_PATH")
  val scriptDir  = scriptPath.substring(0, scriptPath.lastIndexOf("/"))
  // An assumed LXC template
  val defaultTemplate = scriptDir + "/templates/lxc-debian"
  // A Debian release
  val defaultDebianRelease = "stretch"
  // A valid time stamp for the Debian Snapshot Archive
  val defaultTimestamp = "20170128T211018Z"

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
        s"http://snapshot.debian.org/archive/debian-security/$timestamp/",
      "--package=" + "fakeroot")) !
  }
}
