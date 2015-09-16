package io.fcomb

import io.fcomb.models._
import io.fcomb.models.comb._
import io.fcomb.models.errors._
import io.fcomb.models.request._
import io.fcomb.models.response._
import java.time.LocalDateTime
import java.util.UUID
import scala.collection.mutable.OpenHashMap
import spray.json._
import spray.json.DefaultJsonProtocol._

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

  implicit val noContentResponseJsonProtocol =
    new JsonWriter[NoContentResponse] {
      def write(n: NoContentResponse) = JsNull
    }

  def createEnumerationJsonFormat[T <: Enumeration](obj: T)(implicit m: Manifest[T]) =
    new RootJsonFormat[T#Value] {
      def write(obj: T#Value) = JsString(obj.toString)

      private val values = OpenHashMap(
        obj.values.toSeq.map(v => (v.toString.toLowerCase, v)): _*
      )

      def read(v: JsValue) = {
        val value =
          if (v.isInstanceOf[JsString])
            values.get(v.asInstanceOf[JsString].value.toLowerCase)
          else None
        value.getOrElse(throw new DeserializationException(s"invalid ${m.getClass.getName.split('.').last} value"))
      }
    }

  implicit val methodKindJsonProtocol = createEnumerationJsonFormat(MethodKind)

  implicit val resetPasswordRequestJsonProtocol = jsonFormat1(ResetPasswordRequest)

  implicit val resetPasswordSetRequestJsonProtocol = jsonFormat2(ResetPasswordSetRequest)

  implicit val changePasswordRequestJsonProtocol = jsonFormat2(ChangePasswordRequest)

  implicit val validationErrorsResponseJsonProtocol = jsonFormat1(ValidationErrors)

  implicit val userSignUpRequestJsonProtocol = jsonFormat4(UserSignUpRequest)

  implicit val userRequestJsonProtocol = jsonFormat3(UserRequest)

  implicit val userResponseJsonProtocol = jsonFormat4(UserResponse)

  implicit val sessionRequestJsonProtocol = jsonFormat2(SessionRequest)

  implicit val sessionResponseJsonProtocol = jsonFormat1(SessionResponse)

  implicit val combRequestJsonProtocol = jsonFormat2(CombRequest)

  implicit val combResponseJsonProtocol = jsonFormat5(CombResponse)

  implicit val combMethodRequestJsonProtocol = jsonFormat3(CombMethodRequest)

  implicit val combMethodResponseJsonProtocol = jsonFormat7(CombMethodResponse)

  object errors {
    implicit object JsonStatusCodeFormat extends RootJsonFormat[ErrorStatus.ErrorStatus] {
      def write(status: ErrorStatus.ErrorStatus) = JsString(status.toString)

      def read(v: JsValue) = throw new NotImplementedError
    }

    implicit val validationFailureResponseFormat = jsonFormat2(MultipleFailureResponse)

    implicit val internalFailureResponseFormat = jsonFormat2(SingleFailureResponse)
  }
}
