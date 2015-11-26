package akka.http

import akka.http.impl.engine.parsing._
import akka.http.impl.engine.parsing.HttpMessageParser.StateResult
import akka.http.impl.engine.parsing.ParserOutput._
import akka.http.impl.engine.rendering._
import akka.http.impl.util._
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers._
import akka.stream._
import akka.stream.scaladsl._
import akka.stream.stage.{Context, Stage, StageState, StatefulStage, SyncDirective}
import akka.util.ByteString
import java.net.InetSocketAddress
import org.slf4j.LoggerFactory
import scala.concurrent._

object HijackTcp {
  private val logger = LoggerFactory.getLogger(this.getClass)

  def outgoingConnection(
    hostname: String,
    port: Int,
    settings: ClientConnectionSettings,
    request: HttpRequest,
    responseFlow: Flow[HttpResponse, Any, Any] = Flow[HttpResponse]
  )(
    implicit
    sys: akka.actor.ActorSystem,
    mat: Materializer
  ) = {
    import sys.dispatcher

    val serverAddress = new InetSocketAddress(hostname, port)

    val valve = StreamUtils.OneTimeValve()
    val httpResponseResult = Promise[HttpResponse]()
    val httpResponseFlow = responseFlow.map(_ => ByteString.empty)

    val renderedInitialRequest = HttpRequestRendererFactory.renderStrict(
      RequestRenderingContext(request, Host(serverAddress)), settings, sys.log
    )

    logger.debug(s"Hijack request: ${renderedInitialRequest.utf8String}")

    val g =
      BidiFlow.fromGraph(FlowGraph.create(httpResponseFlow) { implicit b => responseFlow =>
        import FlowGraph.Implicits._

        val networkIn = b.add(Flow[ByteString].transform(() ⇒
          new HijackStage(request, httpResponseResult, valve, settings)))
        val streamIn = b.add(Flow[ByteString])

        val mergeOut = b.add(Concat[ByteString](3))
        val requestSource = b.add(Source.single(renderedInitialRequest) ++ valve.source)

        val httpResponseSource = b.add(Source(httpResponseResult.future))

        requestSource ~> mergeOut.in(0)
        mergeOut.out ~> streamIn
        httpResponseSource ~> responseFlow ~> mergeOut.in(2)

        BidiShape(
          mergeOut.in(1),
          streamIn.outlet,
          networkIn.inlet,
          networkIn.outlet
        )
      })

    val tcpFlow = Tcp().outgoingConnection(serverAddress, None, settings.socketOptions,
      halfClose = true, settings.connectingTimeout, settings.idleTimeout)

    g.joinMat(tcpFlow)(Keep.right)
      .mapMaterializedValue(_.map(_ => ()))
  }

  // def wstest()(implicit sys: akka.actor.ActorSystem, mat: Materializer) = {
  //   import sys.dispatcher
  //   import scala.concurrent.duration._

  //   val settings =
  //     ClientConnectionSettings(sys).copy(idleTimeout = Duration.Inf) match {
  //       case s => s.copy(parserSettings = s.parserSettings.copy(
  //         maxContentLength = Long.MaxValue
  //       ))
  //     }

  //   val req = WebsocketRequest(
  //     Uri(s"ws://coreos:2375/containers/$name/attach/ws?stream=1&stdout=1&stderr=1"),
  //     extraHeaders = List(
  //       Upgrade(List(UpgradeProtocol("tcp"))),
  //       Origin("http://coreos:2375")
  //     )
  //   )

  //   val closeConnectin = Promise[TextMessage]()

  //   Http()
  //     .websocketClientFlow(req, settings = settings, log = sys.log)
  //     .runWith(Source(closeConnectin.future).drop(1), Sink.foreach(println))
  //     ._2
  //     .onComplete(println)
  // }

  private class HijackStage(
    req: HttpRequest,
    httpResponseResult: Promise[HttpResponse],
    valve: StreamUtils.OneTimeValve,
    settings: ClientConnectionSettings
  ) extends StatefulStage[ByteString, ByteString] {
    type State = StageState[ByteString, ByteString]

    def initial: State = parsingResponse

    def parsingResponse: State = new State {
      val parser = new HttpResponseParser(settings.parserSettings, HttpHeaderParser(settings.parserSettings)()) {
        var first = true

        override protected def parseMessage(input: ByteString, offset: Int): StateResult = {
          if (first) {
            first = false
            super.parseMessage(input, offset)
          } else {
            emit(RemainingBytes(input.drop(offset)))
            terminate()
          }
        }
      }
      parser.setRequestMethodForNextResponse(req.method)

      def onPush(elem: ByteString, ctx: Context[ByteString]): SyncDirective = {
        parser.onPush(elem) match {
          case NeedMoreData ⇒ ctx.pull()
          case ResponseStart(status, protocol, headers, entity, close) ⇒
            val response = HttpResponse(status, headers, protocol = protocol)
            httpResponseResult.success(response)

            become(transparent)
            valve.open()

            val parseResult = parser.onPull()
            require(parseResult == ParserOutput.MessageEnd, s"parseResult should be MessageEnd but was $parseResult")
            parser.onPull() match {
              case NeedMoreData ⇒ ctx.pull()
              case RemainingBytes(bytes) ⇒
                if (bytes.nonEmpty) ctx.push(bytes)
                else ctx.pull()
            }
        }
      }
    }

    def transparent: State = new State {
      def onPush(elem: ByteString, ctx: Context[ByteString]): SyncDirective =
        ctx.push(elem)
    }
  }
}
