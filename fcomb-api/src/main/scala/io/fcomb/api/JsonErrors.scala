package io.fcomb.api

import akka.http.scaladsl.unmarshalling.Unmarshaller._
import akka.http.scaladsl.model._, ContentTypes.`application/json`
import akka.http.scaladsl.server._
import argonaut._, Argonaut._, Shapeless._

case class DeserializationException(msg: String) extends Throwable(msg)

trait JsonErrors { this: JsonHelpers =>
  // object JsonErrorStatus extends Enumeration {
  //   type JsonErrorStatus = Value
  //   val Validation = Value("validation")
  //   val Internal = Value("internal")
  //   val Authorization = Value("authorization")
  //   val Persistent = Value("persistent")
  // }
  //
  // implicit val errorStatusEncodeJson: EncodeJson[JsonErrorStatus.JsonErrorStatus] =
  //   EncodeJson((s: JsonErrorStatus.JsonErrorStatus) => jString(s.toString))
  //
  // sealed trait JsonErrorResponse {
  //   val kind: JsonErrorStatus.JsonErrorStatus
  // }
  //
  // case class MultipleFailureResponse(
  //   kind:   JsonErrorStatus.JsonErrorStatus,
  //   errors: Map[String, List[String]]
  // ) extends JsonErrorResponse
  //
  // case class SingleFailureResponse(
  //   kind:  JsonErrorStatus.JsonErrorStatus,
  //   error: String
  // ) extends JsonErrorResponse
  //
  // implicit val mfpEncodeJson: EncodeJson[MultipleFailureResponse] =
  //   implicitly[EncodeJson[MultipleFailureResponse]]
  //
  // implicit val sfpEncodeJson: EncodeJson[SingleFailureResponse] =
  //   implicitly[EncodeJson[SingleFailureResponse]]
  //
  // def jsonResponse[T](item: Option[T])(implicit ej: EncodeJson[T]): HttpResponse =
  //   jsonResponse(StatusCodes.OK, item)
  //
  // def jsonResponse[T](statusCode: StatusCode, itemOpt: Option[T])(implicit ej: EncodeJson[T]): HttpResponse =
  //   itemOpt match {
  //     case Some(item) => jsonResponse(statusCode, item)
  //     case None       => recordNotFound
  //   }
  //
  // def jsonNotFound(status: JsonErrorStatus.JsonErrorStatus, name: String) = jsonResponse(
  //   StatusCodes.NotFound,
  //   SingleFailureResponse(status, s"$name not found")
  // )
  //
  // val recordNotFound = jsonNotFound(JsonErrorStatus.Persistent, "record")
  //
  // val resourceNotFound = jsonNotFound(JsonErrorStatus.Internal, "resource")
  //
  // val unknownError = jsonResponse(
  //   StatusCodes.InternalServerError,
  //   SingleFailureResponse(JsonErrorStatus.Internal, "Unknown error")
  // )
  //
  // def unauthorizedError(message: String) = jsonResponse(
  //   StatusCodes.Unauthorized,
  //   SingleFailureResponse(JsonErrorStatus.Authorization, message)
  // )
  //
  // // TODO
  // def handleException(e: Throwable): HttpResponse = e match {
  //   case _: DeserializationException | _: ParsingException | _: UnsupportedContentTypeException =>
  //     jsonResponse(
  //       StatusCodes.BadRequest,
  //       SingleFailureResponse(JsonErrorStatus.Internal, e.getMessage)
  //     )
  //   case _ => jsonResponse(
  //     StatusCodes.InternalServerError,
  //     SingleFailureResponse(JsonErrorStatus.Internal, e.getMessage)
  //   )
  // }
  //
  // // TODO
  // def handleRejection(r: Rejection): HttpResponse = r match {
  //   case _ => jsonResponse(
  //     StatusCodes.BadRequest,
  //     SingleFailureResponse(JsonErrorStatus.Internal, r.toString)
  //   )
  // }
}

object JsonErrors extends JsonHelpers with JsonErrors
