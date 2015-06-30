package io.fcomb.api.services

import io.fcomb.api.{ JsonHelpers, JsonErrors }
import io.fcomb.utils.validation._
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.RequestContext
import akka.http.scaladsl.unmarshalling.{ FromEntityUnmarshaller, Unmarshal, Unmarshaller }
import akka.http.scaladsl.marshalling.ToResponseMarshallable
import akka.http.scaladsl.model.ContentTypes.`application/json`
import akka.stream.ActorMaterializer
import scala.language.implicitConversions
import scala.concurrent.{ ExecutionContext, Future }
import scala.collection.mutable
import spray.json._
import shapeless._, contrib.scalaz._, syntax.std.function._
import scalaz._, scalaz.syntax.foldable._

trait JsonService extends JsonHelpers with JsonErrors {
  type ValidationResponse[T] = Validation[NonEmptyList[(String, String)], T]

  def jsonResponse[T](statusCode: StatusCode, res: ValidationResponse[T])(implicit w: JsonWriter[T]): HttpResponse = {
    res match {
      case Success(r) => jsonResponse[T](StatusCodes.OK, r)
      case Failure(e) => jsonErrorsResponse(e)
    }
  }

  def jsonResponse[T](statusCode: StatusCode, f: Future[ValidationResponse[T]])(implicit ec: ExecutionContext, w: JsonWriter[T]): Future[HttpResponse] =
    f flatMap {
      case Success(r) => Future.successful(jsonResponse(statusCode, r))
      case Failure(e) => Future.successful(jsonErrorsResponse(e))
    }

  def jsonResponse[T](statusCode: StatusCode, res: ValidationResponse[Future[ValidationResponse[T]]])(implicit ec: ExecutionContext, w: JsonWriter[T]): Future[HttpResponse] = res match {
    case Success(f) => f.map(jsonResponse[T](statusCode, _))
    case Failure(e) => Future.successful(jsonErrorsResponse(e))
  }

  def jsonResponse[T](res: ValidationResponse[Future[ValidationResponse[T]]])(implicit ec: ExecutionContext, w: JsonWriter[T]): Future[HttpResponse] =
    jsonResponse(StatusCodes.OK, res)

  def jsonResponse[T](res: Future[T])(implicit ec: ExecutionContext, w: JsonWriter[T]): Future[HttpResponse] =
    res.map(jsonResponse[T](_))

  def jsonResponse[T](item: T)(implicit w: JsonWriter[T]): HttpResponse =
    jsonResponse(StatusCodes.OK, item)

  def jsonErrorsResponse(e: NonEmptyList[(String, String)]): HttpResponse =
    jsonErrorsResponse(StatusCodes.UnprocessableEntity, e)

  def jsonErrorsResponse(statusCode: StatusCode, e: NonEmptyList[(String, String)]): HttpResponse =
    jsonResponse(statusCode, mapErrors(e))

  implicit def unmarshaller(implicit ec: ExecutionContext, materializer: ActorMaterializer): FromEntityUnmarshaller[JsValue] =
    Unmarshaller.byteStringUnmarshaller.forContentTypes(`application/json`).mapWithCharset { (data, charset) ⇒
      if (data.nonEmpty) data.decodeString(charset.nioCharset.name()).parseJson
      else JsNull
    }

  def jsonRequest[T](f: JsObject => T)(implicit ec: ExecutionContext, materializer: ActorMaterializer, conv: T => ToResponseMarshallable) = { ctx: RequestContext =>
    ctx.complete(Unmarshal(ctx.request.entity).to[JsValue].map { json =>
      conv(f(json.asJsObject))
    })
  }

  def complete[T](f: => T)(implicit ec: ExecutionContext, materializer: ActorMaterializer, conv: T => ToResponseMarshallable) = { ctx: RequestContext =>
    ctx.complete(conv(f))
  }

  def mapErrors(list: NonEmptyList[(String, String)]) = {
    val errors = list.foldLeft(Map[String, List[String]]()) { (acc, e) =>
      acc.+((e._1, acc.getOrElse(e._1, List[String]()).+:(e._2)))
    }
    MultipleFailureResponse(JsonErrorStatus.Validation, errors)
  }
}
