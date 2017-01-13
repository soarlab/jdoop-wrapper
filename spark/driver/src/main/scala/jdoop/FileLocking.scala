package jdoop

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
