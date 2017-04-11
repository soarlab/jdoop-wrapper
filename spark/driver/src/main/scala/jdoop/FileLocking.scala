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


import Constants._
import java.io._
import java.nio.channels.{FileChannel, FileLock, OverlappingFileLockException}

class FileLocking {
  private val file = new File(cpuCoresFilePath)
  private val raFile = new RandomAccessFile(file, "rw")
  val fileChannel = raFile.getChannel()

  private var lock: FileLock = null
  while (lock == null) {
    try {
      lock = fileChannel.tryLock()
    } catch {
      case e: OverlappingFileLockException => Thread.sleep(100)
    }
  }

  def release(): Unit = {
    lock.release()
    raFile.close()
  }
}
