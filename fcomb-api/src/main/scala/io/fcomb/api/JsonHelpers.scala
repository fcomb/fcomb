package io.fcomb.api

import akka.http.scaladsl.model._, ContentTypes.`application/json`
import argonaut._

trait JsonHelpers {
  // def jsonResponse[T](statusCode: StatusCode, item: T)(implicit j: EncodeJson[T]): HttpResponse =
  //   jsonResponse(statusCode, j(item))
  //
  // def jsonResponse(statusCode: StatusCode, value: Json): HttpResponse = {
  //   val entity = statusCode match {
  //     case StatusCodes.NoContent =>
  //       HttpEntity.empty(`application/json`)
  //     case _ =>
  //       HttpEntity(`application/json`, value.toString)
  //   }
  //   HttpResponse(
  //     status = statusCode,
  //     entity = entity
  //   )
  // }
}
