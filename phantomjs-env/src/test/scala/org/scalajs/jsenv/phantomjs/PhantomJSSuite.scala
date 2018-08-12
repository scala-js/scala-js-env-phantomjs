package org.scalajs.jsenv.phantomjs

import org.scalajs.jsenv.test._

import org.junit.runner.RunWith

@RunWith(classOf[JSEnvSuiteRunner])
class PhantomJSSuite extends JSEnvSuite(PhantomJSSuite.Config)

object PhantomJSSuite {
  val Config = {
    JSEnvSuiteConfig(new PhantomJSEnv)
      .withTerminateVMJSCode("__ScalaJSEnv.exitFunction(0)")
      .withSupportsTimeout(false) // #20
  }
}
