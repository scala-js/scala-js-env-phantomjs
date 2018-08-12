package org.scalajs.jsenv.phantomjs

import scala.annotation.tailrec

import java.io._

private[phantomjs] object Utils {
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
