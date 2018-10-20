package org.scalajs.jsenv.phantomjs

import scala.concurrent.duration._

import org.junit.Test

import org.scalajs.jsenv.test.kit.TestKit

class PhantomJSEnvTest {
  private val kit = new TestKit(new PhantomJSEnv, 1.minute)

  private def replyTest(msg: String) = {
    kit.withComRun(s"""scalajsCom.init(scalajsCom.send);""") {
      _.send(msg)
        .expectMsg(msg)
        .expectNoMsgs()
        .closeRun()
    }
  }

  // Some whitebox tests related to fragmentation of long messages

  @Test
  def need3BytesPerCharTest: Unit = {
    replyTest("\u0fff" * 20000)
  }

  @Test
  def noCutInTheMiddleOfASurrogatePair: Unit = {
    val manySurrogates = "\ud83d\udca9" * 20000
    replyTest(manySurrogates) // in case a default cut is on an odd offset
    replyTest("A" + manySurrogates) // in case it is on an even offset
  }
}
