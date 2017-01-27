#!/usr/bin/env scala

import java.io.File
import scala.util.matching.Regex
import sys.process._

object Patch {
  def patch23jwbf(path: String): Int = {
    require(new File(path).isDirectory)

    // A file has to be renamed because its source code is completely
    // commented out, which breaks JDoop's source file indexing needed
    // for testing. The file is
    // src/main/java/net/sourceforge/jwbf/mediawiki/contentRep/SomeArticle.java

    val dir = Seq(
      path,
      "src",
      "src/main/java/net/sourceforge/jwbf/mediawiki/contentRep"
    ).mkString("/")
    val baseName = "SomeArticle.java"
    Process(
      Seq("mv", baseName, baseName + ".nonexisting"),
      Some(new java.io.File(dir))
    ).!
  }

  def patch26jipa(path: String): Unit = {
    require(new File(path).isDirectory)

    // Source files were directly in src/main/java, without the
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
    val sf110Root =
      if (args.length == 1) args(0)
      else "/mnt/storage/sf110"
    Map(
      "23_jwbf"   -> (patch23jwbf _),
      "26_jipa"   -> (patch26jipa _),
      "27_gangup" -> (patch27gangup _),
      "94_jclo"   -> (patch94jclo _)
    ) foreach { case ((benchmark, patch)) =>
        patch(Seq(sf110Root, benchmark).mkString("/"))
    }
  }
}
