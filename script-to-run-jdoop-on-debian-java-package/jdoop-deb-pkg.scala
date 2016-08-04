#!/bin/bash
exec scala -nowarn "$0" "$@"
!#

import java.io.{File, FileOutputStream}
import java.nio.file.{Files, Path}
import java.util.Enumeration
import java.util.jar.{JarFile, JarEntry, Manifest}
import scala.collection.JavaConverters._
import scala.util.matching.Regex
import Stream._
import sys.process._

object runJDoop {

  def getSrcPkgName(binPkgName: String): String = {
    val shellOut = ("apt-cache show " + binPkgName) lines_! ProcessLogger(
      (o: String) => ())
    shellOut.dropWhile(!_.startsWith("Source: ")) match {
      case Stream.Empty => binPkgName
      case remainingLines => remainingLines(0).split(": ")(1).split(" ")(0)
    }
  }

  def getSrcPkgVer(srcPkgName: String): String = {
    val shellOut = ("apt-cache showsrc " + srcPkgName) lines_! ProcessLogger(
      (o: String) => ())
    shellOut.dropWhile(!_.startsWith("Version: "))(0)
      .split(": ")(1).split("-")(0)
  }

  def getSrcPkgDir(srcPkgName: String): String =
    srcPkgName + "-" + getSrcPkgVer(srcPkgName)

  def downloadSrcPkg(srcPkgName: String): Int = {
    ("apt-get source " + srcPkgName) ! ProcessLogger(
      (o: String) => (), (e: String) => ())
  }

  /**
    *  Recursively searches all files matching a regular expression.
    * 
    *  @param f A root directory where the search should start
    *  @param r A regular expression matching names of files to be returned
    *  @return A stream of files found by the search
    */
  def recursiveListFiles(f: File, r: Regex): Stream[File] = {
    require(f.isDirectory())

    val currentDirFiles = f.listFiles.toStream
    val matching = currentDirFiles.filter(
      f => r.findFirstIn(f.getName).isDefined)
      .toStream
    matching append currentDirFiles.filter(_.isDirectory)
      .flatMap(recursiveListFiles(_, r))
  }

  /**
    * Determines the root of .java files for an unpacked Debian Java
    * source package
    */
  def javaSrcDir(srcPkgName: String): File = {
    val dir = getSrcPkgDir(srcPkgName)
    val javaFiles = recursiveListFiles(new File(dir),
      """.*\.java""".r)
      .map { _.toString.substring((dir + File.separator).length) }
    val srcDirMap = javaFiles groupBy { f =>
      f.substring(0, f.indexOf(File.separator)) }
    val fileCountMap = srcDirMap map { kv => (kv._1, kv._2.length) }
    val mainSrcDir = fileCountMap.toSeq.sortBy(_._2).last._1
    Seq("main" + File.separator + "java", "java", "").map { d: String =>
      val nd: String = mainSrcDir + File.separator + d
      (nd, (new File(dir + File.separator + nd)).exists)
    }
      .filter { _._2 }
      .map { p => new File(p._1) }
      .apply(0)
  }

  def createTmpDir(): Path = Files.createTempDirectory("jdoop-")

  def installBinPkg(binPkgName: String): Int =
    ("sudo apt-get install --assume-yes " + binPkgName) ! ProcessLogger(
      (o: String) => (), (e: String) => ())

  /**
    * Finds all JAR files within a Debian binary package
    *
    * @param binPkgName The name of the Debian binary package to search
    * @return A set of JAR files
    */
  def binPkgJarFiles(binPkgName: String): Set[File] = {
    val shellOut = ("dpkg --listfiles " + binPkgName) lines_! ProcessLogger(
      (o: String) => ())
    shellOut.filter { l =>
      l.startsWith("/usr/share/java") && l.endsWith(".jar") }
      .map { new File(_) }
      .filter { f => !Files.isSymbolicLink(f.toPath) }
      .toSet
  }

  /**
    * Gets JAR manifest entries
    * 
    * @param jar The JAR file to get manifest entries from
    * @return A sequence of JAR manifest entries
    */
  def getJarManifestEntries(jar: File): Seq[JarEntry] = {
    import scala.collection.mutable.ArrayBuffer

    val buf = new ArrayBuffer[JarEntry]
    val jar_ = new JarFile(jar)
    val enum = jar_.entries()
    while (enum.hasMoreElements)
      buf += enum.nextElement()

    buf.toSeq
  }

  /**
    * Unpacks a collection of JAR files to a directory
    * 
    * @param jars A set of JAR files to unpack
    * @param dir A directory to unpack to
    */
  def unpackJars(jars: Set[File], dir: Path) {
    def unpackJar(jar: File) {
      val jar_ = new JarFile(jar)
      def processEntry(entry: JarEntry) {
        val outFile = new File(dir + File.separator + entry.getName)
        if (entry.isDirectory)
          outFile.mkdir()
        else {
          val is = jar_.getInputStream(entry)
          val fos = new FileOutputStream(outFile)
          while (is.available > 0)
            fos.write(is.read())
          fos.close()
          is.close()
        }
      }

      getJarManifestEntries(jar) foreach { processEntry }
      // val enum = jar.entries()
      // while (enum.hasMoreElements) {
      //   val elem = enum.nextElement()
      //   val outFile = new File(dir + File.separator + elem.getName)
      //   if (elem.isDirectory)
      //     outFile.mkdir()
      //   else {
      //     val is = jar.getInputStream(elem)
      //     val fos = new FileOutputStream(outFile)
      //     while (is.available > 0)
      //       fos.write(is.read())
      //     fos.close()
      //     is.close()
      //   }
      // }
    }
    jars foreach { unpackJar(_) }
  }

  /**
    * Gets the fully qualified domain name of the main Java package in
    * a given JAR file
    * 
    * @param jar The JAR file to extract the name from
    * @return The fully qualified name of the Java package
    */
  def getJavaPackageName(jar: JarFile): String = {
    def guessFromDirStruct: String = {
      "TODO, i.e. to be figured out"
    }
    val manifest = jar.getManifest
    // an immutable map; it would have been a mutable one without .toMap
    val entryMap = manifest.getEntries.asScala.toMap
    entryMap.keySet.toSeq match {
      case Seq() => guessFromDirStruct
      case seq => seq.sortBy(_.length).apply(0).split('/').mkString(".")
    }
  }

  def main(args: Array[String]) {
    val testPkgs = List("libxom-java", "libglazedlists-java", "libmapscript-java")
    for (binPkgName <- testPkgs) {
      println("======================================")
      println("Binary package: " + binPkgName)
      val srcPkgName = getSrcPkgName(binPkgName)
      println("Source package: " + srcPkgName)
      downloadSrcPkg(srcPkgName)
      println(javaSrcDir(srcPkgName))
      installBinPkg(binPkgName)
      val tmpDir = createTmpDir()
      println(tmpDir)
      unpackJars(binPkgJarFiles(binPkgName), tmpDir)
      println("Java package name: " +
        getJavaPackageName(new JarFile(binPkgJarFiles(binPkgName).toSeq.apply(0))))
    }
  }
}
