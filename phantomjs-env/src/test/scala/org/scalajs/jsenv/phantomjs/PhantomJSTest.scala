package org.scalajs.jsenv.phantomjs

import org.scalajs.jsenv.test._

import org.junit.{Ignore, Test}

class PhantomJSTest extends JSEnvTest with ComTests {
  protected def newJSEnv: PhantomJSEnv = new PhantomJSEnv

  // Disabled du to #10
  @Test
  @Ignore
  override def syntaxErrorTest: Unit =
    super.syntaxErrorTest
}
