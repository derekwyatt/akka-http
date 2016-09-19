package akka.http.impl.engine.http2

import akka.http.scaladsl.model.{ HttpMethods, HttpRequest }
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{ Sink, Source }
import akka.testkit.AkkaSpec
import akka.util.ByteString
import org.scalatest.concurrent.ScalaFutures

class RequestHeaderHpackDecompressionSpec extends AkkaSpec with ScalaFutures {
  implicit val mat = ActorMaterializer()

  // test data from: https://github.com/twitter/hpack/blob/master/hpack/src/test/resources/hpack
  val encodedGET = "82"
  val encodedPOST = "83"
  val encodedPathSamplePath = "040c 2f73 616d 706c 652f 7061 7468"

  "RequestHeaderDecompression" must {
    "decompress spec-example-1 to right path (Uri)" in {
      val headerBlock = encodedPathSamplePath

      val bytes = parseHeaderBlock(headerBlock)
      val frames = List(HeadersFrame(0, false, true, bytes))

      val request = runToRequest(frames)
      request.uri.toString should ===("/sample/path")
    }
    "decompress spec-example-2 to POST HttpMethod" in {
      val headerBlock = encodedPOST

      val bytes = parseHeaderBlock(headerBlock)
      val frames = List(HeadersFrame(0, false, true, bytes))

      val request = runToRequest(frames)
      request.method should ===(HttpMethods.POST)
    }
    "decompress given CONTINUATION Headers frames" in {
      val streamId = 0
      val frames = List(
        HeadersFrame(streamId, false, endHeaders = false, headerBlockFragment = parseHeaderBlock(encodedPathSamplePath)),
        HeadersFrame(streamId, false, endHeaders = true, headerBlockFragment = parseHeaderBlock(encodedPOST)),
        // this would be a new request (should NOT apply to the first emitted request):
        HeadersFrame(streamId, false, endHeaders = true, headerBlockFragment = parseHeaderBlock(encodedGET))
      )

      val request = runToRequest(frames)
      request.method should ===(HttpMethods.POST)
      request.uri.toString should ===("/sample/path")
    }

    "decompress example request w/o huffman coding from spec - C.3.1 - first request" in pending
    "decompress example request w/o huffman coding from spec - C.3.2 - second request" in pending
    "decompress example request w/o huffman coding from spec - C.3.3 - third request" in pending

    "decompress example request w huffman coding from spec - C.4.1 - first request" in pending
    "decompress example request w huffman coding from spec - C.4.2 - second request" in pending
    "decompress example request w huffman coding from spec - C.4.3 - third request" in pending
  }

  def runToRequest(frames: List[HeadersFrame]): HttpRequest = {
    Source.fromIterator(() ⇒ frames.iterator)
      .via(new RequestHeaderHpackDecompression)
      .runWith(Sink.head)
      .futureValue
  }

  def parseHeaderBlock(data: String): ByteString = {
    val bytes = data.replaceAll(" ", "").toCharArray.grouped(2).map(ch ⇒ Integer.parseInt(new String(ch), 16).toByte).toVector
    ByteString(bytes: _*)
  }
}
