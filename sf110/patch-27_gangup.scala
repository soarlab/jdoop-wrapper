#!/usr/bin/env scala

import java.io.File
import sys.process._

object Patch {
  def usage(): Unit = {
    println("A path to the root of 27_gangup is expected!")
    sys.exit(1)
  }

  def patch27gangup(path: String): Unit = {
    require(new File(path).isDirectory)

    // Some source files were not in the right place:
    //
    // - util/MapEdit.java -> MapEdit.java
    // - admingui/AdminGUI.java -> AdminGUI.java
    
    val dir = new File(Seq(path, "src", "src/main/java").mkString("/"))
    Map(
      "util/MapEdit.java" -> "MapEdit.java",
      "admingui/AdminGUI.java" -> "AdminGUI.java"
    ) foreach { case ((from, to)) =>
        Process(Seq("mv", from, to), Some(dir)).!
    }
  }

  def main(args: Array[String]): Unit = {
    if (args.length != 1) usage()
    patch27gangup(args(0))
  }
}
