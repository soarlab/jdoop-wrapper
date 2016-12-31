#!/usr/bin/env scala

import java.io.File
import sys.process._

object Patch {
  def usage(): Unit = {
    println("A path to the root of 94_jclo is expected!")
    sys.exit(1)
  }

  def patch94jclo(path: String): Unit = {
    require(new File(path).isDirectory)

    // Some source files were not in the right place:
    //
    // - edu/mscd/cs/jclo/Main.java -> Main.java
    // - edu/mscd/cs/jclo/Example.java -> Example.java
    
    val dir = new File(Seq(path, "src", "src/main/java").mkString("/"))
    Map(
      "edu/mscd/cs/jclo/Main.java" -> "Main.java",
      "edu/mscd/cs/jclo/Example.java" -> "Example.java"
    ) foreach { case ((from, to)) =>
        Process(Seq("mv", from, to), Some(dir)).!
    }
  }

  def main(args: Array[String]): Unit = {
    if (args.length != 1) usage()
    patch94jclo(args(0))
  }
}
