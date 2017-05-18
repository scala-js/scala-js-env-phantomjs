inThisBuild(Seq(
  version := "0.1.0-SNAPSHOT",
  scalaVersion := "2.11.11"
))

name := "sbt-plugin-test"

lazy val jetty9 = project.
  enablePlugins(ScalaJSPlugin).
  settings(
    name := "Scala.js sbt test with jetty9 on classpath",
    // Use PhantomJS, allow cross domain requests
    jsEnv := PhantomJSEnv(args = Seq("--web-security=no")).value,
    Jetty9Test.runSetting
  )
