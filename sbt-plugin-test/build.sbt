inThisBuild(Seq(
  version := "0.1.0-SNAPSHOT",
  scalaVersion := "2.11.12",
  // PhantomJS does not support ES 2015
  scalaJSLinkerConfig ~= { _.withESFeatures(_.withUseECMAScript2015(false)) }
))

name := "sbt-plugin-test"

lazy val jetty9 = project.
  enablePlugins(ScalaJSPlugin).
  settings(
    name := "Scala.js sbt test with jetty9 on classpath",
    // Use PhantomJS, allow cross domain requests
    jsEnv := {
      PhantomJSEnv(
          org.scalajs.jsenv.phantomjs.PhantomJSEnv.Config()
            .withArgs(List("--web-security=no"))
      ).value
    },
    Jetty9Test.runSetting
  )
