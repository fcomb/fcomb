package io.fcomb.docker.api

import io.fcomb.docker.api.methods._
import akka.actor.ActorSystem
import akka.stream.{Materializer, OverflowStrategy}
import akka.stream.scaladsl._
import akka.stream.io.Framing
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.ContentTypes.`application/json`
import akka.http.scaladsl.model.MediaTypes.`application/x-tar`
import akka.http.scaladsl.model.headers.{UpgradeProtocol, Upgrade}
import akka.http.scaladsl.Http
import akka.util.ByteString
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.collection.immutable
import spray.json._
import spray.json.DefaultJsonProtocol._
import akka.http._
import org.slf4j.Logger

private[api] trait ApiConnection {
  protected val logger: Logger

  val host: String
  val port: Int

  implicit val sys: ActorSystem
  implicit val mat: Materializer

  import sys.dispatcher

  // TODO: add TLS
  private def connectionFlow(duration: Option[Duration] = None) = {
    val settings = duration.foldLeft(ClientConnectionSettings(sys)) {
      (s, d) => s.copy(idleTimeout = d)
    } match { // TODO: https://github.com/akka/akka/issues/16468
      case s => s.copy(parserSettings = s.parserSettings.copy(
        maxContentLength = Long.MaxValue
      ))
    }
    Http().outgoingConnection(host, port, settings = settings)
      .buffer(10, OverflowStrategy.backpressure)
  }

  private def uriWithQuery(uri: Uri, queryParams: Map[String, String]) =
    uri.withQuery(Uri.Query(queryParams.filter(_._2.nonEmpty)))

  protected def apiRequestAsSource(
    method: HttpMethod,
    uri: Uri,
    queryParams: Map[String, String] = Map.empty,
    entity: RequestEntity = HttpEntity.Empty,
    idleTimeout: Option[Duration] = None,
    headers: immutable.Seq[HttpHeader] = immutable.Seq.empty
  ) = {
    Source
      .single(HttpRequest(
        uri = uriWithQuery(uri, queryParams),
        method = method,
        entity = entity,
        headers = headers
      ))
      .via(connectionFlow(idleTimeout))
      .runWith(Sink.head)
      .flatMap { res =>
        if (res.status.isSuccess()) Future.successful(res)
        else {
          entity.dataBytes.runFold(new StringBuffer) { (acc, bs) =>
            acc.append(bs.utf8String)
          }.map { buf =>
            val msg = buf.toString()
            res.status.intValue() match {
              case 400 => throw new BadParameterException(msg)
              case 403 => throw new PermissionDeniedException(msg)
              case 404 => throw new ResouceOrContainerNotFoundException(msg)
              case 406 => throw new ImpossibleToAttachException(msg)
              case 500 => throw new ServerErrorException(msg)
              case _ => throw new UnknownException(msg)
            }
          }
        }
      }
  }

  protected def requestJsonEntity[T <: DockerApiRequest](body: T)(
    implicit jw: JsonWriter[T]
  ) =
    HttpEntity(`application/json`, body.toJson.compactPrint)

  protected def requestTarEntity(source: Source[ByteString, Any]) =
    HttpEntity(`application/x-tar`, source)

  protected def apiJsonRequestAsSource(
    method: HttpMethod,
    uri: Uri,
    queryParams: Map[String, String] = Map.empty,
    entity: RequestEntity = HttpEntity.Empty
  ) =
    apiRequestAsSource(method, uri, queryParams, entity).map { data =>
      data.entity.dataBytes.map(_.utf8String.parseJson)
    }

  protected def apiJsonRequest(
    method: HttpMethod,
    uri: Uri,
    queryParams: Map[String, String] = Map.empty,
    entity: RequestEntity = HttpEntity.Empty
  ) =
    apiRequestAsSource(method, uri, queryParams, entity)
      .flatMap(_.entity.dataBytes.runFold(ByteString.empty)(_ ++ _))
      .map { res =>
        val s = res.utf8String
        logger.debug(s"Docker API response: $s")
        s.parseJson
      }
}
