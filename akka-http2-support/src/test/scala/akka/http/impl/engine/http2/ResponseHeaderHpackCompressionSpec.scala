package akka.http.impl.engine.http2

import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers._
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Sink
import akka.stream.scaladsl.Source
import akka.testkit.AkkaSpec
import org.scalatest.concurrent.ScalaFutures

class ResponseHeaderHpackCompressionSpec extends AkkaSpec with ScalaFutures {
  implicit val mat = ActorMaterializer()

  "HttpResponseHeaderHpackCompression" must {
    "compress example-c6-1 OK response status code" in {
      // example from: https://http2.github.io/http2-spec/compression.html#rfc.section.C.6.1

      /*
        :status: 302
        cache-control: private
        date: Mon, 21 Oct 2013 20:13:21 GMT
        location: https://www.example.com
       */
      val headers = List(
        `Cache-Control`(CacheDirectives.`private`(Nil)),
        Date(DateTime(2013, 10, 21, 20, 13, 21, 1, 0, false)),
        Location("https://www.example.com")
      )
      val response = HttpResponse(status = StatusCodes.Found)
        .withHeaders(headers)

      val event = runToSingleFrameEvent(response)

      val expectedHeaderBlockFragment =
        """|4882 6402 5885 aec3 771a 4b61 96d0 7abe 
           |9410 54d4 44a8 2005 9504 0b81 66e0 82a6 
           |2d1b ff6e 919d 29ad 1718 63c7 8f0b 97c8 
           |e9ae 82ae 43d3
           |"""
      assertBlockFragment(event, expectedHeaderBlockFragment)
    }
  }

  def runToSingleFrameEvent(response: HttpResponse): HeadersFrame = {
    Source.single(response)
      .via(new ResponseHeaderHpackCompression)
      .runWith(Sink.head)
      .futureValue
  }

  def assertBlockFragment(event: HeadersFrame, expectedHeaderBlockFragment: String): Unit = {
    val got = event.headerBlockFragment.map(_ formatted "%02x").grouped(2).map(e ⇒ e.mkString).mkString(" ")
    val expected = expectedHeaderBlockFragment.stripMargin.replaceAll("\n", "").trim
    got should ===(expected)
  }

}
