package akka.http

import akka.actor.ActorSystem
import akka.http.impl.engine.parsing._
import akka.http.impl.engine.parsing.HttpMessageParser.StateResult
import akka.http.impl.engine.parsing.ParserOutput._
import akka.http.impl.engine.rendering._
import akka.http.impl.util._
import akka.http.scaladsl.settings.ClientConnectionSettings
import akka.http.scaladsl.ConnectionContext
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers._
import akka.stream._
import akka.stream.TLSProtocol._
import akka.stream.scaladsl._
import akka.stream.stage.{ Context, StageState, StatefulStage, SyncDirective }
import akka.util.ByteString
import java.net.InetSocketAddress
import javax.net.ssl.SSLContext
import org.slf4j.LoggerFactory
import scala.concurrent.Promise

object HijackTcp {
  private val logger = LoggerFactory.getLogger(this.getClass)

  def outgoingConnection(
    hostname:     String,
    port:         Int,
    settings:     ClientConnectionSettings,
    request:      HttpRequest,
    responseFlow: Flow[HttpResponse, Any, Any] = Flow[HttpResponse],
    sslEngine:    Option[SSLContext]           = None
  )(
    implicit
    sys: ActorSystem,
    mat: Materializer
  ) = {
    import sys.dispatcher

    val serverAddress = new InetSocketAddress(hostname, port)

    val valve = StreamUtils.OneTimeValve()
    val httpResponseResult = Promise[HttpResponse]()
    val httpResponseFlow = responseFlow.map(_ ⇒ ByteString.empty)

    val renderedInitialRequest = HttpRequestRendererFactory.renderStrict(
      RequestRenderingContext(request, Host(serverAddress)),
      settings,
      sys.log
    )

    logger.debug(s"Hijack request: ${renderedInitialRequest.utf8String}")

    val g = BidiFlow.fromGraph(
      GraphDSL.create(httpResponseFlow) { implicit b ⇒ responseFlow ⇒
        import GraphDSL.Implicits._

        val networkIn = b.add(Flow[ByteString].transform(() ⇒
          new HijackStage(request, httpResponseResult, valve, settings)))
        val streamIn = b.add(Flow[ByteString])

        val mergeOut = b.add(Concat[ByteString](3))
        val requestSource =
          b.add(Source.single(renderedInitialRequest) ++ valve.source)

        val httpResponseSource =
          b.add(Source.fromFuture(httpResponseResult.future))

        requestSource ~> mergeOut.in(0)
        mergeOut.out ~> streamIn
        httpResponseSource ~> responseFlow ~> mergeOut.in(2)

        BidiShape(
          mergeOut.in(1),
          streamIn.outlet,
          networkIn.in,
          networkIn.outlet
        )
      }
    )

    val transportFlow = Tcp().outgoingConnection(
      serverAddress,
      None,
      settings.socketOptions,
      halfClose = true,
      settings.connectingTimeout,
      settings.idleTimeout
    )
    val connectionFlow = sslEngine match {
      case Some(ctx) ⇒
        val hctx = ConnectionContext.https(ctx)
        val hostInfo = Some(hostname → port)
        TLS(ctx, hctx.firstSession, Client, hostInfo = hostInfo)
          .joinMat(transportFlow)(Keep.right)
          .join(tlsFlow)
      case _ ⇒ transportFlow
    }

    g.joinMat(connectionFlow)(Keep.right).mapMaterializedValue(_.map(_ ⇒ ()))
  }

  private val tlsFlow = {
    val wrapTls = Flow[ByteString].map(SendBytes)
    val unwrapTls = Flow[SslTlsInbound].collect {
      case SessionBytes(_, bytes) ⇒ bytes
    }
    BidiFlow.fromFlows(unwrapTls, wrapTls)
  }

  private class HijackStage(
    req:                HttpRequest,
    httpResponseResult: Promise[HttpResponse],
    valve:              StreamUtils.OneTimeValve,
    settings:           ClientConnectionSettings
  )
      extends StatefulStage[ByteString, ByteString] {
    type State = StageState[ByteString, ByteString]

    def initial: State = parsingResponse

    def parsingResponse: State = new State {
      val parser = new HttpResponseParser(
        settings.parserSettings,
        HttpHeaderParser(settings.parserSettings)()
      ) {
        var first = true
        override def handleInformationalResponses = false
        override protected def parseMessage(
          input: ByteString, offset: Int
        ): StateResult = {
          if (first) {
            first = false
            super.parseMessage(input, offset)
          }
          else {
            emit(RemainingBytes(input.drop(offset)))
            terminate()
          }
        }
      }
      parser.setContextForNextResponse(
        HttpResponseParser.ResponseContext(HttpMethods.GET, None)
      )

      def onPush(elem: ByteString, ctx: Context[ByteString]): SyncDirective = {
        parser.parseBytes(elem) match {
          case NeedMoreData ⇒ ctx.pull()
          case ResponseStart(status, protocol, headers, entity, close) ⇒
            val response = HttpResponse(status, headers, protocol = protocol)
            httpResponseResult.success(response)

            become(transparent)
            valve.open()

            val parseResult = parser.onPull()
            require(
              parseResult == ParserOutput.MessageEnd,
              s"parseResult should be MessageEnd but was $parseResult"
            )
            parser.onPull() match {
              case NeedMoreData ⇒ ctx.pull()
              case RemainingBytes(bytes) ⇒
                if (bytes.nonEmpty) ctx.push(bytes)
                else ctx.pull()
              case m ⇒
                logger.error(s"Unexpected message: $m")
                throw new IllegalStateException(s"Unexpected message: $m")
            }
          case m ⇒
            logger.error(s"Unexpected message: $m")
            throw new IllegalStateException(s"Unexpected message: $m")
        }
      }
    }

    def transparent: State = new State {
      def onPush(elem: ByteString, ctx: Context[ByteString]): SyncDirective =
        ctx.push(elem)
    }
  }
}
