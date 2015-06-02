package io.fcomb.api

import akka.http.scaladsl.model._, ContentTypes.`application/json`
import spray.json._

trait JsonHelpers extends DefaultJsonProtocol {
  def jsonResponse[T](statusCode: StatusCode, item: T)(implicit w: JsonWriter[T]): HttpResponse =
    jsonResponse(statusCode, w.write(item).toJson)

  def jsonResponse(statusCode: StatusCode, value: JsValue): HttpResponse = {
    val entity = statusCode match {
      case StatusCodes.NoContent =>
        HttpEntity.empty(`application/json`)
      case _ =>
        HttpEntity(`application/json`, value.toJson.toString)
    }
    HttpResponse(
      status = statusCode,
      entity = entity
    )
  }
}
