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
// along with maline.  If not, see <http://www.gnu.org/licenses/>.


import Constants.totalCpuCores
import java.nio.ByteBuffer
import java.nio.channels.FileChannel

trait CPUCoresUtil {

  type CoreSet = Set[Int]
  object CoreSet {
    def apply(xs: Int*): CoreSet = Set(xs: _*)
  }

  protected def availableCores(implicit fileChannel: FileChannel): CoreSet = {
    val buf = ByteBuffer.allocate(totalCpuCores)
    fileChannel.position(0)
    fileChannel.read(buf)
      (0 until totalCpuCores).foldRight(CoreSet()){ (i, set) =>
        if (buf.array()(i) == 1) set + i else set
      }
  }

  protected def coresToByteBuffer(cores: CoreSet): ByteBuffer = {
    val buf = ByteBuffer.allocate(totalCpuCores)
    buf.put(
      cores.foldRight(Vector.fill(totalCpuCores)(0: Byte)){(core, vec) =>
        vec.updated(core, 1: Byte)
      }.toArray,
      0,
      totalCpuCores
    ).flip()
    buf
  }

  protected def updateAvailableCores(left: CoreSet)
    (implicit fileChannel: FileChannel): Unit = {
    val buf = coresToByteBuffer(left)
    fileChannel.position(0)
    fileChannel.write(buf)
    fileChannel.force(false)
  }
}

object CPUCoresUtil {
  def generateFile(): Unit = {
    val fl = new FileLocking
    implicit val fileChannel = fl.fileChannel

    (new CPUCoresUtil {}).updateAvailableCores((0 until totalCpuCores).toSet)

    fl.release()
  }
}

object GetContainerCores extends CPUCoresUtil {
  private def obtainCores(from: CoreSet, n: Int): (CoreSet, CoreSet) = {
    require(from.size >= n)

    val obtained = from take n
    (obtained, from -- obtained)
  }

  def apply(n: Int): CoreSet = {
    val fl = new FileLocking
    implicit val fileChannel = fl.fileChannel

    val (obtained, left) = obtainCores(availableCores, n)
    updateAvailableCores(left)
    fl.release()

    obtained
  }
}

object ReleaseContainerCores extends CPUCoresUtil {
  private def releaseCores(a: CoreSet, b: CoreSet): CoreSet = {
    require((a intersect b).isEmpty)

    a union b
  }

  def apply(cores: CoreSet): CoreSet = {
    val fl = new FileLocking
    implicit val fileChannel = fl.fileChannel

    val updated = releaseCores(cores, availableCores)
    updateAvailableCores(updated)
    fl.release()

    updated
  }
}
