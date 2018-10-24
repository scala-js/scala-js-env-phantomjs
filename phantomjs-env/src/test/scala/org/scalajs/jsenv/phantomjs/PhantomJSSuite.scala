package org.scalajs.jsenv.phantomjs

import org.scalajs.jsenv.test._

import org.junit.runner.RunWith

import scala.collection.JavaConverters._

import org.junit.runner.Runner
import org.junit.runners.Suite
import org.junit.runner.manipulation.Filter
import org.junit.runner.Description

@RunWith(classOf[PhantomJSSuiteRunner])
class PhantomJSSuite private (private[phantomjs] val _config: JSEnvSuiteConfig)
    extends JSEnvSuite(_config) {

  def this() = this(JSEnvSuiteConfig(new PhantomJSEnv))
}

/** A runner that wraps `JSEnvSuiteRunner` and ignores `largeMessageTest`.
 *
 *  This works around [[https://github.com/scala-js/scala-js/issues/3476]]:
 *  `largeMessageTest` sends messages that are not valid UTF-16 strings, which
 *  is invalid.
 */
final class PhantomJSSuiteRunner private (
    root: Class[_], base: JSEnvSuiteRunner)
    extends Suite(root, List[Runner](base).asJava) {

  /** Constructor for reflective instantiation via `@RunWith`. */
  def this(suite: Class[_ <: PhantomJSSuite]) =
    this(suite, new JSEnvSuiteRunner(suite.newInstance()._config))

  // Apply a filter that ignores all tests whose method name is `largeMessageTest`
  base.filter(new Filter {
    def describe(): String = "Ignore largeMessageTest"

    def shouldRun(description: Description): Boolean = {
      description.getMethodName == null ||
      !description.getMethodName.startsWith("largeMessageTest")
    }
  })
}
