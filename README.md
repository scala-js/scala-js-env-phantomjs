# scalajs-env-phantomjs

`scalajs-env-phantomjs` is a JavaScript environment for Scala.js (a `JSEnv`)
running [PhantomJS](http://phantomjs.org/).

This repository contains `scalajs-env-phantomjs` for Scala.js 1.x. In Scala.js
0.6.x, the PhantomJS environment is part of the core distribution.

## Setup

```scala
// project/plugins.sbt
addSbtPlugin("org.scala-js" % "sbt-scalajs-env-phantomjs" % "1.0.0")

// build.sbt (inside .settings(...) for multi-project builds)
jsEnv := PhantomJSEnv().value
scalaJSLinkerConfig ~= { _.withESFeatures(_.withUseECMAScript2015(false)) }
```

The last line is needed because Scala.js emits ECMAScript 2015 code by default,
but PhantomJS only supports ES 5.1.

See [the Scaladoc](https://javadoc.io/doc/org.scala-js/scalajs-env-phantomjs_2.13/latest/org/scalajs/jsenv/phantomjs/index.html) for other configuration options.
