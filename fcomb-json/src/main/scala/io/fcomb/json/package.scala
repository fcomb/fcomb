package io.fcomb

import io.fcomb.models._
import io.fcomb.models.docker._
import io.fcomb.models.errors._
import spray.json._
import spray.json.DefaultJsonProtocol._

package object json {
  private def createEnumJsonFormat[T <: enumeratum.EnumEntry](enum: enumeratum.Enum[T]) =
    new RootJsonFormat[T] {
      def write(obj: T) = JsString(obj.toString)

      private val klassName =
        enum.getClass.getName.split('.').last.dropRight(1).replaceAll("\\$", "#")

      def read(v: JsValue) = {
        val value =
          if (v.isInstanceOf[JsString])
            Some(enum.withName(v.asInstanceOf[JsString].value))
          else None
        value.getOrElse(throw new DeserializationException(s"invalid $klassName value"))
      }
    }

  implicit val paginationDataFormat = jsonFormat1(PaginationData)

  implicit def multipleDataResponse[A: JsonFormat] =
    jsonFormat2(MultipleDataResponse.apply[A])

  implicit val sessionJsonProtocol = jsonFormat1(Session)

  object errors {
    implicit val errorMessageFormat: RootJsonFormat[ErrorMessage] = ???
    implicit val failureResponseFormat: RootJsonFormat[FailureResponse] = ???
  }
}
