package org.scalajs.jsenv.phantomjs

import scala.annotation.tailrec

import java.io._
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.{Files, Path}

import com.google.common.jimfs.Jimfs

private[phantomjs] object Utils {
  def createMemFile(path: String, content: String): Path =
    Files.write(Jimfs.newFileSystem().getPath(path), content.getBytes(UTF_8))

  def pipeInputStreamToOutputStream(in: InputStream,
      out: OutputStream): Unit = {
    try {
      val buf = new Array[Byte](4096)

      @tailrec
      def loop(): Unit = {
        val read = in.read(buf)
        if (read != -1) {
          out.write(buf, 0, read)
          loop()
        }
      }

      loop()
    } finally {
      in.close()
    }
  }
}
