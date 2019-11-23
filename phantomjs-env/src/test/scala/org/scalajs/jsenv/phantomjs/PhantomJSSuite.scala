package org.scalajs.jsenv.phantomjs

import org.scalajs.jsenv.test._

import org.junit.runner.RunWith

@RunWith(classOf[JSEnvSuiteRunner])
class PhantomJSSuite extends JSEnvSuite(JSEnvSuiteConfig(new PhantomJSEnv))
