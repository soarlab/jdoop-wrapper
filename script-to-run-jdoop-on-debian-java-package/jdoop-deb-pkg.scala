#!/usr/bin/env scala

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

  def getSrcPkgDir(srcPkgName: String): String = {
    val v = getSrcPkgVer(srcPkgName)
    srcPkgName + "-" + (
      if (v.contains(':')) v.substring(v.indexOf(':') + 1) else v
    )
  }

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
    matching.append(currentDirFiles.filter(_.isDirectory)
      .flatMap(recursiveListFiles(_, r)))
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
  def getBinPkgJars(binPkgName: String): Set[File] = {
    val shellOut = ("dpkg --listfiles " + binPkgName) lines_! ProcessLogger(
      (o: String) => ())
    shellOut.filter { l =>
      l.startsWith("/usr/share/java") && l.endsWith(".jar") }
      .map { new File(_) }
      .filter { f => !Files.isSymbolicLink(f.toPath) }
      .toSet
  }

  /**
    * Gets JAR entries
    * 
    * @param jar The JAR file to get entries from
    * @return A sequence of JAR entries
    */
  def getJarEntries(jar: File): Seq[JarEntry] = {
    val buf = new scala.collection.mutable.ArrayBuffer[JarEntry]
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
    /** Unpacks one JAR file */
    def unpackJar(jar: File) {
      val jar_ = new JarFile(jar)
      /** Process a single entry in a JAR file. For a regular file it means
        * copying to a destination directory, and for a directory it
        * means creating it. */
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

      getJarEntries(jar) foreach { processEntry }
    }
    jars foreach { unpackJar }
  }

  /**
    * Gets a Java sub-package name from a path
    * @param path A path to a file (e.g., .java, .class)
    * @param depth The depth of the package name, this many from the top
    * @return The Java package name of the file based on its path
    */
  def getSubPkgName(path: String, depth: Int = 1): String = {
    require(depth > 0)
    path
      .split("/")
      // consider replacing last with lastOption. Same elsewhere where
      // I access the (0) element of a sequence
      .dropRight(if (path.split("/").last.contains('.')) 1 else 0)
      .take(depth)
      .mkString(".")
  }

  /**
    * Returns a collection of file paths normalized to package names
    * that are grouped by common package name prefixes determined
    * by a depth parameter.
    * 
    * @param files A sequence of file paths
    * @param depth The depth of package name groups to be taken
    * @return A grouped collection of packages corresponding to the files
    */
  def groupByPkg(files: Seq[String], depth: Int = 1): Seq[Seq[String]] = {
    require(depth > 0)

    files.map { getSubPkgName(_, depth) }
      .groupBy(p => p)
      .values
      .toSeq
      .sortBy(-_.length)
  }

  /**
    * Gets the fully qualified domain name of the main Java package in
    * a given JAR file
    * 
    * @param jar The JAR file to extract the name from
    * @return The fully qualified name of the Java package
    */
  def getJavaPackageName(jar: File): String = {
    def guessFromDirStruct: String = {
      val classFiles = getJarEntries(jar).filter {
        _.getName.endsWith(".class") }
        .map { _.toString }

      val s = Stream.from(1).map { groupByPkg(classFiles, _) }
      val rootPkgs = s(0)

      rootPkgs.length match {
        // Either all classes are in the same root package so find the
        // longest common prefix...
        case 1 =>
          val sWithIndices = s zipWithIndex
          val commonPrefix = sWithIndices takeWhile{ pkgs =>
            pkgs._1.length == 1 &&
            (pkgs._2 match {
              case 0 => true
              case i => sWithIndices(i - 1)._1 != pkgs._1
            })
          }
          commonPrefix.last._1(0)(0)
        // ... or there is more than one root package so return one
        // with the most classes in it. All packages not returned here
        // will not be tested by JDoop.
        case n => rootPkgs(0)(0)
      }
    }

    val manifest = new JarFile(jar).getManifest
    // an immutable map; it would have been a mutable one without .toMap
    val entryMap = manifest.getEntries.asScala.toMap
    entryMap.keySet.toSeq match {
      case Seq() => guessFromDirStruct
      case seq => seq.sortBy(_.length).apply(0).split('/').mkString(".")
    }
  }

  def main(args: Array[String]) {
    // val testPkgs = List("libxom-java", "libglazedlists-java", "libmapscript-java")
    val testPkgs = Seq(
      // "libswingx1-java",
      "libswt-gtk-3-java")// ,
      // "libtomcat7-java",
      // "libtrilead-ssh2-java",
      // "libvecmath-java",
      // "libvldocking-java",
      // "libwagon-java",
      // "libwagon2-java",
      // "libxalan2-java")

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
      unpackJars(getBinPkgJars(binPkgName), tmpDir)
      println("Java package name: " +
        getJavaPackageName(getBinPkgJars(binPkgName).toSeq.apply(0)))
    }
  }
}
