#!/bin/bash
exec scala -nowarn "$0" "$@"
!#

import sys.process._

object FindLibs {

  def dummyTest: Boolean = {
    val output = """Package: libxom-java
Source: xom
Version: 1.2.10-1
Installed-Size: 186
Maintainer: Debian Java Maintainers <pkg-java-maintainers@lists.alioth.debian.org>
Architecture: all
Depends: libjaxen-java (>= 1.1~beta8), libxerces2-java
Suggests: libxom-java-doc
Description-en: New XML object model for Java
 XOM(tm) is a new XML object model. It is an open source (LGPL),
 tree-based API for processing XML with Java that strives for correctness,
 simplicity, and performance, in that order.
 .
 XOM is designed to be easy to learn and easy to use. It works very
 straight-forwardly, and has a very shallow learning curve. Assuming
 you're already familiar with XML, you should be able to get up and
 running with XOM very quickly.
 .
 XOM is the only XML API that makes no compromises on correctness.
 XOM only accepts namespace well-formed XML documents, and only allows
 you to create namespace well-formed XML documents. (In fact, it's a
 little stricter than that: it actually guarantees that all documents
 are round-trippable and have well-defined XML infosets.) XOM manages
 your XML so you don't have to. With XOM, you can focus on the unique
 value of your application, and trust XOM to get the XML right.
Description-md5: a8d1a9a18a1e7ad5b84a130a26b5a216
Homepage: http://www.xom.nu
Tag: implemented-in::java, works-with-format::xml
Section: java
Priority: optional
Filename: pool/main/x/xom/libxom-java_1.2.10-1_all.deb
Size: 169654
MD5sum: d11441e52fbc186b621fc416877a83c6
SHA1: 10ee3977d28160500597b5e73b43a29494ec2571
SHA256: f8433bb03b887934a45d4081a374ee09ebe0eba066cac399722cedc128c7d3d9
"""
    val inter = output split("\n") dropWhile {
      s => ("""implemented-in::java""".r.findFirstIn(s)) == None
    }
    inter foreach { println }
    !inter.isEmpty
  }

  def isImplementedInJava(pkgName: String): Boolean = {
    // println(s"Now checking if $pkgName is implemented in Java...")
    val shellOutput = ("apt-cache show " + pkgName) lines_! ProcessLogger(
      (o: String) => ())
    !(shellOutput.
      dropWhile {
        s => ("""implemented-in::java""".r.findFirstIn(s)) == None
      }
      .isEmpty)
  }

  def main(args: Array[String]) {
    // println(dummyTest)
    val shellOutput = "apt-cache search java" lines_! ProcessLogger(
      (o: String) => ())
    // for(i <- 0 until shellOutput.length)
    //   println("Item " + i + "\n---------\n" + shellOutput(i))
    val finalList = shellOutput
      .map { s => s.split(" ")(0) }
      .filter { s => s.startsWith("lib") && !s.endsWith("-doc") }
      .filter { isImplementedInJava }
    finalList foreach { println }
  }
}
