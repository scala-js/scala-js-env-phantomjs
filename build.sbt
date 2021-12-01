val scalaJSVersion = "1.0.0"

inThisBuild(Seq(
  version := "1.0.1-SNAPSHOT",
  organization := "org.scala-js",

  crossScalaVersions := Seq("2.11.12", "2.12.10", "2.13.1"),
  sbtVersion := "1.2.8",
  scalaVersion := "2.12.10",
  scalacOptions ++= Seq("-deprecation", "-feature", "-Xfatal-warnings"),

  homepage := Some(url("https://www.scala-js.org/")),
  licenses += ("BSD New",
      url("https://github.com/scala-js/scala-js-env-phantomjs/blob/main/LICENSE")),
  scmInfo := Some(ScmInfo(
      url("https://github.com/scala-js/scala-js-env-phantomjs"),
      "scm:git:git@github.com:scala-js/scala-js-env-phantomjs.git",
      Some("scm:git:git@github.com:scala-js/scala-js-env-phantomjs.git")))
))

val commonSettings = Def.settings(
  // Scaladoc linking
  apiURL := {
    val name = moduleName.value
    val v = version.value
    Some(url(s"https://www.scala-js.org/api/$name/$v/"))
  },
  autoAPIMappings := true,

  publishMavenStyle := true,
  publishTo := {
    val nexus = "https://oss.sonatype.org/"
    if (isSnapshot.value)
      Some("snapshots" at nexus + "content/repositories/snapshots")
    else
      Some("releases" at nexus + "service/local/staging/deploy/maven2")
  },
  pomExtra := (
    <developers>
      <developer>
        <id>sjrd</id>
        <name>Sébastien Doeraene</name>
        <url>https://github.com/sjrd/</url>
      </developer>
      <developer>
        <id>gzm0</id>
        <name>Tobias Schlatter</name>
        <url>https://github.com/gzm0/</url>
      </developer>
      <developer>
        <id>nicolasstucki</id>
        <name>Nicolas Stucki</name>
        <url>https://github.com/nicolasstucki/</url>
      </developer>
    </developers>
  ),
  pomIncludeRepository := { _ => false }
)

lazy val root: Project = project.in(file(".")).
  settings(
    publishArtifact in Compile := false,
    publish := {},
    publishLocal := {},

    clean := clean.dependsOn(
      clean in `scalajs-env-phantomjs`,
      clean in `sbt-scalajs-env-phantomjs`
    ).value
  )

lazy val `scalajs-env-phantomjs`: Project = project.in(file("phantomjs-env")).
  settings(
    commonSettings,

    libraryDependencies ++= Seq(
      "org.scala-js" %% "scalajs-js-envs" % scalaJSVersion,
      "com.google.jimfs" % "jimfs" % "1.1",
      "org.eclipse.jetty" % "jetty-websocket" % "8.1.16.v20140903" % "provided",
      "org.eclipse.jetty" % "jetty-server" % "8.1.16.v20140903" % "provided",

      "com.novocode" % "junit-interface" % "0.11" % "test",
      "org.scala-js" %% "scalajs-js-envs-test-kit" % scalaJSVersion % "test",
      "org.scala-js" %% "scalajs-env-nodejs" % scalaJSVersion % "test"
    ),

    testOptions += Tests.Argument(TestFrameworks.JUnit, "-v", "-a", "-s")
  )

lazy val `sbt-scalajs-env-phantomjs`: Project = project.in(file("phantomjs-sbt-plugin")).
  settings(
    commonSettings,

    sbtPlugin := true,
    scalaBinaryVersion :=
      CrossVersion.binaryScalaVersion(scalaVersion.value),

    addSbtPlugin("org.scala-js" % "sbt-scalajs" % scalaJSVersion),

    // Add API mappings for sbt (seems they don't export their API URL)
    apiMappings ++= {
      val deps = (externalDependencyClasspath in Compile).value
      val sbtJars = deps filter { attributed =>
        val p = attributed.data.getPath
        p.contains("/org.scala-sbt/") && p.endsWith(".jar")
      }
      val docUrl =
        url(s"http://www.scala-sbt.org/${sbtVersion.value}/api/")
      sbtJars.map(_.data -> docUrl).toMap
    }
  ).
  dependsOn(`scalajs-env-phantomjs`)
