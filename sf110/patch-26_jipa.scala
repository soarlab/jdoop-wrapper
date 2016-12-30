#!/usr/bin/env scala

import java.io.File
import scala.util.matching.Regex
import sys.process._

object Patch {
  def usage(): Unit = {
    println("A path to the root of 26_jipa is expected!")
    sys.exit(1)
  }

  def patch26jipa(path: String): Unit = {
    require(new File(path).isDirectory)

    // source files were directly in src/main/java, without the
    // package subdirectory jipa so move them to src/main/java/jipa.
    
    val dir = new File(Seq(path, "src", "src/main/java").mkString("/"))
    val pkgDir = "jipa"
    Process(Seq("mkdir", "-p", pkgDir), Some(dir)).!
    val javaFiles = dir.listFiles.filter(f =>
      """.*\.java""".r.findFirstIn(f.getName).isDefined)
    javaFiles foreach { f =>
      Process(Seq("mv", f.getName, pkgDir), Some(dir)).!
    }
  }

  def main(args: Array[String]): Unit = {
    if (args.length != 1) usage()
    patch26jipa(args(0))
  }
}
