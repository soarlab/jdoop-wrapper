package jdoop

// Copyright 2017 Marko Dimjašević
//
// This file is part of jdoop-wrapper.
//
// jdoop-wrapper is free software: you can redistribute it and/or modify it
// under the terms of the GNU Affero General Public License as
// published by the Free Software Foundation, either version 3 of the
// License, or (at your option) any later version.
//
// jdoop-wrapper is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU Affero General Public License for more details.
//
// You should have received a copy of the GNU Affero General Public License
// along with jdoop-wrapper.  If not, see <http://www.gnu.org/licenses/>.


case class SF110Project(projectDir: String)

case class Task(
  project: SF110Project,
  containerName: String,
  timelimit: Int,
  hostBenchmarkDir: String,
  hostWorkDir: String,
  masterNodeDir: java.io.File,
  tool: Tool) extends Ordered[Task] {

  /**
    * Compares tasks according to how long the accompanying benchmarks
    * take to execute from observation by running JDoop on it, in
    * descending order. This is implemented because several benchmarks
    * take a lot of time to execute so it is better to start running
    * them as soon as possible in a parallel pipeline.
    */
  def compare(that: Task): Int =
    (project.projectDir, that.project.projectDir) match {
      case ("2_a4j", _)          => -1
      case ("1_tullibee", _)     => -1
      case ("6_jnfe", _)         => -1
      case ("23_jwbf", _)        => -1
      case ("9_falselight", _)   => -1
      case ("53_shp2kml", _)     => -1
      case ("31_xisemele", _)    => -1
      case ("22_byuic", _)       => -1

      case (_, "2_a4j")          => 1
      case (_, "1_tullibee")     => 1
      case (_, "6_jnfe")         => 1
      case (_, "23_jwbf")        => 1
      case (_, "9_falselight")   => 1
      case (_, "53_shp2kml")     => 1
      case (_, "31_xisemele")    => 1
      case (_, "22_byuic")       => 1

      case _                     => 0
    }
}
