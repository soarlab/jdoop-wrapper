#!/usr/bin/env scala

import sys.process._

object Patch {
  def usage(): Unit = {
    println("A path to the root of 23_jwbf is expected!")
    sys.exit(1)
  }

  def patch23jwbf(path: String): Int = {
    // A file has to be renamed because its source code is completely
    // commented out, which breaks JDoop's source file indexing needed
    // for testing. The file is
    // src/main/java/net/sourceforge/jwbf/mediawiki/contentRep/SomeArticle.java
    val dir = Seq(path, "src", "src/main/java/net/sourceforge/jwbf/mediawiki/contentRep").mkString("/")
    val baseName = "SomeArticle.java"
    Process(
      Seq("mv", baseName, baseName + ".nonexisting"),
      Some(new java.io.File(dir))
    ).!
  }

  def main(args: Array[String]): Unit = {
    if (args.length != 1) usage()
    patch23jwbf(args(0))
  }
}
