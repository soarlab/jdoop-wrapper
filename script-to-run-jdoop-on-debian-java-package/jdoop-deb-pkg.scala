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

  // A list of common directory structures for a source code tree
  val srcSeq = List(
    List("src", "main", "java"),
    List("src", "java", "main"),
    List("src", "java"),
    List("main", "java"),
    List("main"),
    List("java"),
    List("src"),
    List("")
  )
  // A list of common directory structures for a source code tree
  val binSeq = List(
    List("build", "classes", "main"),
    List("build", "classes"),
    List("output", "build"),
    List("dist", "classes"),
    List("classes"),
    List("target"),
    List("output"),
    List("build"),
    List("bin"),
    List("")
  )

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
      .split(": ")(1).split("-").dropRight(1).mkString("-")
  }

  def getSrcPkgDir(srcPkgName: String): String = {
    val v = getSrcPkgVer(srcPkgName)
    srcPkgName + "-" + v.substring(v.indexOf(':') + 1)
  }

  /**
    * Gets a set of names of binary packages that a source package builds.
    *
    * @param srcPkgName The name of the input source package
    * @return A set of binary package names that the source package builds
    */
  def getSrcBinPkgs(srcPkgName: String): Set[String] = {
    val shellOut = ("apt-cache showsrc " + srcPkgName) lines_! ProcessLogger(
      (o: String) => ())
    shellOut.dropWhile(!_.startsWith("Binary: "))(0)
      .split(": ")(1).split(",").map{_.trim}.toSet
  }

  def downloadSrcPkg(srcPkgName: String): Int = {
    ("apt-get source " + srcPkgName) ! ProcessLogger(
      (o: String) => (), (e: String) => ())
  }

  def installBuildDeps(srcPkgName: String): Int = {
    ("sudo apt-get build-dep --assume-yes " + srcPkgName) ! ProcessLogger(
      (o: String) => (), (e: String) => ())
  }

  /**
    * Builds a Debian source package.
    *
    * This function assumes the package has been downloaded already.
    *
    * @param srcPkgName The source package to build
    */
  def buildSrcPkg(srcPkgName: String) = {
    require(new File(getSrcPkgDir(srcPkgName)).isDirectory())

    Process(
      Seq("fakeroot", "debian/rules", "build"),
      new File(getSrcPkgDir(srcPkgName)),       // The directory to
                                                // execute the command
                                                // in.
      "DEB_BUILD_OPTIONS" -> "nocheck"          // env vars
    ).!!
  }

  def installBinPkgs(binPkgNames: Set[String]): Int =
    ("sudo apt-get install --assume-yes " +
      binPkgNames.mkString(" ")) ! ProcessLogger(
      (o: String) => (), (e: String) => ())

  def installBinPkg(binPkgName: String): Int = installBinPkgs(Set(binPkgName))

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

  def createTmpDir(): Path = Files.createTempDirectory("jdoop-")

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
      // jars can be under /usr/lib and /usr/share
      l.startsWith("/usr") && l.endsWith(".jar") }
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
  def unpackJars(jars: Set[File], dir: Path): Unit = {
    /** Unpacks one JAR file */
    def unpackJar(jar: File) {
      val jar_ = new JarFile(jar)
      /** Process a single entry in a JAR file. For a regular file it means
        * copying to a destination directory, and for a directory it
        * means creating it. */
      def processEntry(entry: JarEntry): Unit = {
        if (!entry.isDirectory) {
          val outFile = new File(dir + File.separator + entry.getName)
          // make sure to create potentially non-existent parent directories
          (new File(outFile.getParent)).mkdirs()

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
      .dropRight(if (path.split(File.separator).last.contains('.')) 1 else 0)
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

  /** Guesses a FQDN of a project based on a directory with most Java
    * (bytecode) files.
    *
    * @param files A sequence of files to base the decision on
    * @return A fully qualified domain name
    */
  def guessFQDNFromDirStruct(files: Seq[String]): String = {
    val s = Stream.from(1).map { groupByPkg(files, _) }
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
      case _ => rootPkgs(0)(0)
    }
  }

  /**
    * Gets the fully qualified domain name of the main Java package in
    * a given JAR file
    * 
    * @param jar The JAR file to extract the name from
    * @return The fully qualified name of the Java package
    */
  def getJavaPackageName(jar: File): String = {

    val manifest = new JarFile(jar).getManifest
    // an immutable map; it would have been a mutable one without .toMap
    val entryMap = manifest.getEntries.asScala.toMap
    entryMap.keySet.toSeq match {
      case Seq() =>
        val classFiles = getJarEntries(jar).filter {
          _.getName.endsWith(".class") }
          .map { _.toString }
        guessFQDNFromDirStruct(classFiles)
      case seq => seq.sortBy(_.length).apply(0).split('/').mkString(".")
    }
  }

  /**
    * Finds the longest common directory structure for Java projects
    *
    * @param topDir the top directory that contains the project
    * @param startDir the directory where the search starts from
    * @param prefixes a list of common directory structure prefixes
    * @return The longest common directory structure
    */
  def longestDirPrefix(
    topDir: String,
    startDir: String,
    prefixes: List[List[String]]): File = {
    prefixes.map { d: List[String] =>
      val newDir: String =
        startDir + File.separator + d.mkString(File.separator)
      (newDir, (new File(topDir + File.separator + newDir)).exists)
    }
      .filter { _._2 }
      .map { p => new File(p._1) }
      .apply(0)
  }

  /**
    * Determines the root of files defined by a regular expression for
    * an unpacked Debian Java source package.
    *
    * @param the name of the source package
    * @param the regular expression that matches files
    * @param prefixes a list of common directory structure prefixes
    * @return The directory where the files are
    */
  def getJavaDir(
    srcPkgName: String,
    r: Regex,
    prefixes: List[List[String]]): File = {
    /**
      * Determines the best source directory candidate starting at a
      * given directory.
      *
      * @param dir The root directory where to search
      * @return The best candidate for the source directory
      */
    def bestDirCandidate(dir: String): String = {
      val files = recursiveListFiles(new File(dir), r)
        .map { _.toString.substring((dir + File.separator).length) }
      val dirMap = files groupBy { f =>
        f.substring(0, f.indexOf(File.separator) max 0) }
      val fileCountMap = dirMap map { kv => (kv._1, kv._2.length) }
      fileCountMap.toSeq.sortBy(_._2).last._1
    }

    val dir = getSrcPkgDir(srcPkgName)
    val mainDir: String = bestDirCandidate(dir)
    val candidateResult: File = longestDirPrefix(dir, mainDir, prefixes)
    // make sure we are not getting into an empty directory
    if (recursiveListFiles(
      new File(dir + File.separator + candidateResult.toString), r).size == 0)
      new File(mainDir)
    else
      candidateResult
  }

  /**
    * Determines the root of .java files for an unpacked Debian Java
    * source package
    *
    * @param sercPkgName The name of the source package
    * @return The root directory
    */
  def getJavaSrcDir(srcPkgName: String): File =
    getJavaDir(srcPkgName, """.*\.java""".r, srcSeq)

  /**
    * Determines the root of .class files for an unpacked Debian Java
    * source package
    *
    * @param sercPkgName The name of the source package
    * @return The root directory
    */
  def getJavaBinDir(srcPkgName: String): File =
    getJavaDir(srcPkgName, """.*\.class""".r, binSeq)

  /**
    * Returns a pair representing a Java source directory and the
    * fully qualified domain name of the Java project in that source
    * directory.
    *
    * @param srcPkgName The name of the source package
    * @return The pair consisting of the source directory and the FQDN
    */
  def getSourceInfo(srcPkgName: String): (String, String) = {
    /**
      * Gets the fully qualified domain name of the main Java package in
      * a given source package
      *
      * @param mainSrcDir A source directory structure before the fully
      *        qualified domain name
      * @return The fully qualified name of the Java package
      */
    def getJavaPackageName(mainSrcDir: String): String = {
      val srcPkgDir  = getSrcPkgDir(srcPkgName)
      val dir = srcPkgDir + File.separator + mainSrcDir
      val javaFiles =
        recursiveListFiles(new File(dir), """.*\.java""".r)
          .map { _.toString.substring((dir + File.separator).length) }
      guessFQDNFromDirStruct(javaFiles)
    }

    def extendWithSneaky(tuple: (String, String),
      sneaky: List[String]): (String, String) = {

      val (javaDir, fqdn) = tuple
      val fqdnList = fqdn.split('.').toList
      if (fqdnList.startsWith(sneaky))
        (javaDir + File.separator + sneaky.mkString(File.separator),
          fqdnList.drop(sneaky.length).mkString("."))
      else
        (javaDir, fqdn)
    }

    val javaDirLvl1 = getJavaSrcDir(srcPkgName).toString
    val fqdnLvl1    = getJavaPackageName(javaDirLvl1)

    val (javaDirLvl2, fqdnLvl2) =
      if (fqdnLvl1.isEmpty)
        // there is a one-level hierarchy and that's at the same time
        // the package name
        (".", javaDirLvl1)
      else
        // there is at least one directory level before the fully
        // qualified domain name directory structure begins
        (javaDirLvl1, fqdnLvl1)

    // check if any usual directory levels are still in the FQDN part
    val (javaDirLvl3, _) = srcSeq.dropRight(1) // drop the empty string at the end
      .foldLeft(
      (javaDirLvl2, fqdnLvl2)){
      (tuple, sneakyFqdn) => extendWithSneaky(tuple, sneakyFqdn)}

    (javaDirLvl3, getJavaPackageName(javaDirLvl3))
  }

  def main(args: Array[String]) {
    val source = scala.io.Source.fromFile(args(0))
    val testPkgs = try source.mkString.split("\n").toSeq finally source.close()

    for (srcPkgName <- testPkgs) {
      println("======================================")
      println("Source package: " + srcPkgName)
      downloadSrcPkg(srcPkgName)
      println("Installing build dependencies...")
      installBuildDeps(srcPkgName)
      val (javaDir, fqdn) = getSourceInfo(srcPkgName)
      println("Java src dir = " + javaDir)
      println("FQDN         = " + fqdn)

      println("Building the package...")
      buildSrcPkg(srcPkgName)
      val buildPkgs = getSrcBinPkgs(srcPkgName)
      println("Builds: " + buildPkgs)
      installBinPkgs(buildPkgs)
      val binDir = getJavaBinDir(srcPkgName)
      println("Class dir = " + binDir)
    }
  }
}
