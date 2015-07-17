package io.fcomb

import io.fcomb.models._
import spray.json._, DefaultJsonProtocol._
import scalaz._, Scalaz._
import java.time.LocalDateTime
import java.util.UUID

package object json {
  implicit object UuidFormat extends RootJsonFormat[UUID] {
    def write(u: UUID) = JsString(u.toString)

    def read(v: JsValue) = v match {
      case JsString(s) => UUID.fromString(s)
      case _ =>
        throw new DeserializationException("invalid UUID")
    }
  }

  implicit object LocalDateTimeFormat extends RootJsonFormat[LocalDateTime] {
    def write(s: LocalDateTime) = JsString(s.toString)

    def read(v: JsValue) = v match {
      case JsString(v) => LocalDateTime.parse(v)
      case _ =>
        throw new DeserializationException("invalid LocalDateTime")
    }
  }

  implicit val validationErrorsResponseJsonProtocol = jsonFormat1(ValidationErrorsResponse)

  implicit val userSignUpRequestJsonProtocol = jsonFormat4(UserSignUpRequest)

  implicit val userRequestJsonProtocol = jsonFormat3(UserRequest)

  implicit val userResponseJsonProtocol = jsonFormat4(UserResponse)

  implicit val sessionRequestJsonProtocol = jsonFormat2(SessionRequest)

  implicit val sessionResponseJsonProtocol = jsonFormat1(SessionResponse)
}
