# scalajs-env-phantomjs

`scalajs-env-phantomjs` is a JavaScript environment for Scala.js (a `JSEnv`)
running [PhantomJS](http://phantomjs.org/).

This repository contains `scalajs-env-phantomjs` for Scala.js 1.x. In Scala.js
0.6.x, the PhantomJS environment is part of the core distribution.

## Setup

```scala
// project/plugins.sbt
addSbtPlugin("org.scala-js" % "sbt-scalajs-env-phantomjs" % "1.0.0-M5")

// build.sbt (inside .settings(...) for multi-project builds)
jsEnv := PhantomJSEnv().value
```
