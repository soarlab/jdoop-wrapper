#!/bin/bash
exec scala -nowarn "$0" "$@"
!#

import sys.process._

object FindLibs {
  def isImplementedInJava(pkgName: String): Boolean = {
    val shellOutput = ("apt-cache show " + pkgName) lines_! ProcessLogger(
      (o: String) => ())
    !shellOutput.
      dropWhile {
        s => ("""implemented-in::java""".r.findFirstIn(s)) == None
      }
      .isEmpty
  }

  def main(args: Array[String]) {
    val shellOutput = "apt-cache search java | sort" lines_! ProcessLogger(
      (o: String) => ())
    val finalList = shellOutput
      .map { s => s.split(" ")(0) }
      .filter { s => s.startsWith("lib") && !s.endsWith("-doc") }
      .filter { isImplementedInJava }
    finalList foreach { println }
  }
}
