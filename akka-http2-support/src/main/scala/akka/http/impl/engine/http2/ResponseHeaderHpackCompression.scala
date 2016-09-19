/**
 * Copyright (C) 2009-2016 Lightbend Inc. <http://www.lightbend.com>
 */

package akka.http.impl.engine.http2

import java.io.OutputStream
import java.nio.{ ByteBuffer, ByteOrder }

import akka.http.scaladsl.model.HttpResponse
import akka.stream.{ Attributes, FlowShape, Inlet, Outlet }
import akka.stream.stage.{ GraphStage, GraphStageLogic, InHandler, OutHandler }
import akka.util.ByteString
import akka.http.impl.util._

/** INTERNAL API */
final class ResponseHeaderHpackCompression extends GraphStage[FlowShape[HttpResponse, HeadersFrame]] {

  import ResponseHeaderHpackCompression._

  // FIXME Make configurable
  final val maxHeaderSize = 4096
  final val maxHeaderTableSize = 4096
  //  final val useIndexing = false
  //  final val forceHuffmanOn = false
  //  final val forceHuffmanOff = false

  val in = Inlet[HttpResponse]("HeaderDecompression.in")
  val out = Outlet[HeadersFrame]("HeaderDecompression.out")

  override def shape = FlowShape.of(in, out)

  // format: OFF
  override def createLogic(inheritedAttributes: Attributes) = new GraphStageLogic(shape)
    with InHandler with OutHandler {
    // format: ON

    val buf = {
      val b = ByteBuffer.allocate(4 * maxHeaderSize) // FIXME, just guessed a number here
      b.order(ByteOrder.LITTLE_ENDIAN)
      b
    }

    // receives bytes to be written from encoder
    private val os = new OutputStream {
      override def write(b: Int): Unit =
        buf.put(b.toByte)
      override def write(b: Array[Byte], off: Int, len: Int): Unit =
        buf.put(b, off, len)
    }

    private val encoder = new com.twitter.hpack.Encoder(maxHeaderTableSize)

    override def onPush(): Unit = {
      val response = grab(in)
      // TODO possibly specialize static table? https://http2.github.io/http2-spec/compression.html#static.table.definition
      // feed `buf` with compressed header data
      encodeAllHeaders(response)

      // render as ByteString
      val len = buf.position()
      buf.limit(len).position(0)

      // FIXME: In order to render a REAL HeadersFrame we need to know the streamId and more info like that
      val headerBlockFragment = ByteString(buf)

      val streamId = 0 // FIXME we need to know the streamId here
      val endStream = false // FIXME, no idea, probably not decided by this stage?
      val endHeaders = true // TODO in our simple impl we always render all into one frame, but we may need to support rendering continuations

      val event = HeadersFrame(streamId, endStream, endHeaders, headerBlockFragment)
      push(out, event)
    }

    def encodeAllHeaders(response: HttpResponse): Unit = {
      encoder.encodeHeader(os, StatusKey, response.status.intValue.toString.getBytes, false) // TODO so wasteful
      response.headers foreach { h ⇒
        // TODO so wasteful... (it needs to be lower-cased since it's checking by == in the LUT)
        val nameBytes = h.name.toRootLowerCase.getBytes
        val valueBytes = h.value.getBytes
        encoder.encodeHeader(os, nameBytes, valueBytes, false)
      }
    }

    override def onPull(): Unit =
      pull(in)

    setHandlers(in, out, this)
  }

}

object ResponseHeaderHpackCompression {
  final val AuthorityKey = ":authority".getBytes
  final val StatusKey = ":status".getBytes
  final val MethofKey = ":method".getBytes

}
