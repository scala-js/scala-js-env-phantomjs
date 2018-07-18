/*                     __                                                   *\
**     ________ ___   / /  ___      __ ____  PhantomJS support for Scala.js **
**    / __/ __// _ | / /  / _ | __ / // __/  (c) 2013-2017, LAMP/EPFL       **
**  __\ \/ /__/ __ |/ /__/ __ |/_// /_\ \    https://www.scala-js.org/      **
** /____/\___/_/ |_/____/_/ | |__/ /____/                                   **
**                          |/____/                                         **
\*                                                                          */

package org.scalajs.jsenv.phantomjs

import scala.collection.mutable
import scala.concurrent.{ExecutionContext, Promise, Future}
import scala.util.Try
import scala.util.control.NonFatal

import java.io._
import java.net._
import java.nio.file.{Files, StandardCopyOption}

import org.scalajs.jsenv._

import org.scalajs.io._
import org.scalajs.io.URIUtils.fixFileURI
import org.scalajs.io.JSUtils.escapeJS

private final class ComRun(jettyClassLoader: ClassLoader,
    runConfig: RunConfig, onMessage: String => Unit,
    startRun: VirtualBinaryFile => JSRun)
    extends JSComRun {

  import ComRun._

  import runConfig.logger

  // Maybe this should be configurable
  private implicit val ec = ExecutionContext.global

  private var state: State = WaitingForServerToStart(Nil)
  private val connectionClosed = Promise[Unit]()

  private[this] val fragmentsBuf = new StringBuilder

  private val promise = Promise[Unit]()

  def future: Future[Unit] = promise.future

  private def loadMgr(): WebsocketManager = {
    val loader =
      if (jettyClassLoader != null) jettyClassLoader
      else getClass().getClassLoader()

    val clazz = loader.loadClass(
        "org.scalajs.jsenv.phantomjs.JettyWebsocketManager")

    val ctors = clazz.getConstructors()
    assert(ctors.length == 1, "JettyWebsocketManager may only have one ctor")

    val listener = new WebsocketListener {
      def onRunning(): Unit = onServerRunning()
      def onOpen(): Unit = onConnectionOpened()
      def onClose(): Unit = onConnectionClosed()
      def onMessage(msg: String): Unit = receiveFrag(msg)
      def log(msg: String): Unit = logger.debug(s"PhantomJS WS Jetty: $msg")
    }

    val mgr = ctors.head.newInstance(listener)

    mgr.asInstanceOf[WebsocketManager]
  }

  private val mgr: WebsocketManager = loadMgr()

  // Constructor
  mgr.start()

  private def onServerRunning(): Unit = synchronized {
    state match {
      case WaitingForServerToStart(sendQueue) =>
        state = AwaitingConnection(sendQueue)
        val comSetup = makeComSetupFile(mgr.localPort)
        val underlyingRun = startRun(comSetup)

        underlyingRun.future.onComplete { result =>
          underlyingRun.close()
          onUnderlyingRunTerminated(result)
        }

      case Closing =>
        // Ignore

      case AwaitingConnection(_) | Connected =>
        throw new IllegalStateException(
            s"Illegal state in onServerRunning: $state")
    }
  }

  private def onConnectionOpened(): Unit = synchronized {
    state match {
      case AwaitingConnection(sendQueue) =>
        sendQueue.reverse.foreach(sendNow)
        state = Connected

      case Closing =>
        mgr.closeConnection()

      case WaitingForServerToStart(_) | Connected =>
        throw new IllegalStateException(
            s"Illegal state in onConnectionOpened: $state")
    }
  }

  private def onConnectionClosed(): Unit = synchronized {
    connectionClosed.success(())

    state match {
      case Connected =>
        state = Closing
        mgr.stop()

      case Closing =>
        // Ignore

      case WaitingForServerToStart(_) | AwaitingConnection(_) =>
        throw new IllegalStateException(
            s"Illegal state in onConnectionClosed: $state")
    }
  }

  private def onUnderlyingRunTerminated(result: Try[Unit]): Unit = synchronized {
    state match {
      case Connected | Closing =>
        // Wait until the connection is closed before completing the promise
        connectionClosed.future.foreach { _ =>
          promise.tryComplete(result)
        }

      case AwaitingConnection(_) =>
        // Complete the promise now
        promise.tryComplete(result)

      case WaitingForServerToStart(_) =>
        throw new IllegalStateException(
            s"Illegal state in onUnderlyingRunTerminated: $state")
    }

    close()
  }

  def send(msg: String): Unit = synchronized {
    state match {
      case WaitingForServerToStart(sendQueue) =>
        state = WaitingForServerToStart(msg :: sendQueue)

      case AwaitingConnection(sendQueue) =>
        state = AwaitingConnection(msg :: sendQueue)

      case Connected =>
        sendNow(msg)

      case Closing =>
        // Ignore
    }
  }

  private def sendNow(msg: String): Unit = {
    val fragParts = msg.length / MaxCharPayloadSize

    for (i <- 0 until fragParts) {
      val payload = msg.substring(
          i * MaxCharPayloadSize, (i + 1) * MaxCharPayloadSize)
      mgr.sendMessage("1" + payload)
    }

    mgr.sendMessage("0" + msg.substring(fragParts * MaxCharPayloadSize))
  }

  private def receiveFrag(frag: String): Unit = synchronized {
    /* If the promise has already been completed, we cannot deliver new
     * messages. This is not supposed to happen.
     */
    assert(!promise.isCompleted)

    /* The fragments are accumulated in an instance-wide buffer in case
     * receiving a non-first fragment times out.
     */
    fragmentsBuf ++= frag.substring(1)

    frag.charAt(0) match {
      case '0' =>
        // Last fragment of a message, send it
        val result = fragmentsBuf.result()
        fragmentsBuf.clear()
        onMessage(result)

      case '1' =>
        // There are more fragments to come; do nothing

      case _ =>
        throw new AssertionError("Bad fragmentation flag in " + frag)
    }
  }

  def close(): Unit = synchronized {
    val oldState = state
    state = Closing

    oldState match {
      case WaitingForServerToStart(_) =>
        mgr.stop()
        // The underlying run will never start, so succeed now.
        promise.trySuccess(())

      case AwaitingConnection(_) =>
        /* Do nothing. We need to allow the already running process to get to
         * the point where it connects, otherwise we won't be able to
         * gracefully stop it. onConnectionOpened() will take care of tearing
         * down the manager.
         */

      case Connected =>
        /* closeConnection() needs to run separately because it is not fully
         * asynchronous, and can otherwise result in deadlocks.
         */
        Future {
          mgr.closeConnection()
        }

      case Closing =>
        // Ignore
    }
  }
}

object ComRun {
  private final val MaxByteMessageSize = 32768 // 32 KB
  private final val MaxCharMessageSize = MaxByteMessageSize / 2 // 2B per char
  private final val MaxCharPayloadSize = MaxCharMessageSize - 1 // frag flag

  private sealed abstract class State

  private final case class WaitingForServerToStart(sendQueue: List[String])
      extends State

  private final case class AwaitingConnection(sendQueue: List[String])
      extends State

  private case object Connected extends State

  private case object Closing extends State

  /** Starts a [[JSComRun]] using the provided [[JSRun]] launcher.
   *
   *  @param jettyClassLoader A ClassLoader to isolate jetty.
   *  @param config Configuration for the run.
   *  @param onMessage callback upon message reception.
   *  @param startRun [[JSRun]] launcher. Gets passed a
   *      [[org.scalajs.io.VirtualBinaryFile VirtualBinaryFile]] that
   *      initializes `scalaJSCom` on `global`. Requires PhantomJS libraries.
   */
  def start(jettyClassLoader: ClassLoader, config: RunConfig,
      onMessage: String => Unit)(
      startRun: VirtualBinaryFile => JSRun): JSComRun = {
    new ComRun(jettyClassLoader, config, onMessage, startRun)
  }

  /** Starts a [[JSComRun]] using the provided [[JSRun]] launcher.
   *
   *  @param config Configuration for the run.
   *  @param onMessage callback upon message reception.
   *  @param startRun [[JSRun]] launcher. Gets passed a
   *      [[org.scalajs.io.VirtualBinaryFile VirtualBinaryFile]] that
   *      initializes `scalaJSCom` on `global`. Requires PhantomJS libraries.
   */
  def start(config: RunConfig, onMessage: String => Unit)(
      startRun: VirtualBinaryFile => JSRun): JSComRun = {
    start(null, config, onMessage)(startRun)
  }

  private def makeComSetupFile(serverPort: Int): VirtualBinaryFile = {
    assert(serverPort > 0,
        s"Manager running with a non-positive port number: $serverPort")

    val code = s"""
      |(function() {
      |  var MaxPayloadSize = $MaxCharPayloadSize;
      |
      |  // Buffers received messages
      |  var inMessages = [];
      |  var receiveFragment = "";
      |
      |  // The callback where received messages go
      |  var onMessage = null;
      |
      |  // Buffer for messages sent before socket is open
      |  var outMsgBuf = [];
      |
      |  // The socket for communication
      |  var websocket = new WebSocket("ws://localhost:$serverPort");
      |
      |  websocket.onopen = function(evt) {
      |    for (var i = 0; i < outMsgBuf.length; ++i)
      |      sendImpl(outMsgBuf[i]);
      |    outMsgBuf = null;
      |  };
      |  websocket.onclose = function(evt) {
      |    websocket = null;
      |    window.callPhantom({ action: 'exit', returnValue: 0 });
      |  };
      |  websocket.onmessage = function(evt) {
      |    var newData = receiveFragment + evt.data.substring(1);
      |    if (evt.data.charAt(0) == "0") {
      |      receiveFragment = "";
      |      if (inMessages !== null)
      |        inMessages.push(newData);
      |      else
      |        onMessage(newData);
      |    } else if (evt.data.charAt(0) == "1") {
      |      receiveFragment = newData;
      |    } else {
      |      throw new Error("Bad fragmentation flag in " + evt.data);
      |    }
      |  };
      |  websocket.onerror = function(evt) {
      |    websocket = null;
      |    window.callPhantom({ action: 'exit', returnValue: 1 });
      |    throw new Error("Websocket failed: " + evt);
      |  };
      |
      |  function sendImpl(msg) {
      |    var frags = (msg.length / MaxPayloadSize) | 0;
      |
      |    for (var i = 0; i < frags; ++i) {
      |      var payload = msg.substring(
      |          i * MaxPayloadSize, (i + 1) * MaxPayloadSize);
      |      websocket.send("1" + payload);
      |    }
      |
      |    websocket.send("0" + msg.substring(frags * MaxPayloadSize));
      |  }
      |
      |  window.scalajsCom = {
      |    init: function(onMsg) {
      |      if (onMessage !== null)
      |        throw new Error("Com already initialized");
      |
      |      onMessage = onMsg;
      |      setTimeout(function() {
      |        for (var i = 0; i < inMessages.length; ++i)
      |          onMessage(inMessages[i]);
      |        inMessages = null;
      |      }, 0);
      |    },
      |    send: function(msg) {
      |      if (websocket === null)
      |        return; // we are closed already. ignore message
      |
      |      if (outMsgBuf !== null)
      |        outMsgBuf.push(msg);
      |      else
      |        sendImpl(msg);
      |    }
      |  }
      |}).call(this);""".stripMargin

    MemVirtualBinaryFile.fromStringUTF8("comSetup.js", code)
  }
}
