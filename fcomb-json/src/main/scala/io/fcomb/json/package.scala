package io.fcomb

import io.fcomb.models._, errors._, node._, application._
import io.fcomb.request._
import io.fcomb.response._
import java.time._
import java.util.UUID
import scala.collection.mutable.OpenHashMap
import scala.collection.immutable.IntMap
import spray.json._
import spray.json.DefaultJsonProtocol._

package object json {
  implicit class JsObjectMethods(val obj: JsObject) extends AnyVal {
    def get[T](fieldName: String)(implicit jr: JsonReader[T]): T =
      obj.fields(fieldName).convertTo[T]

    def getOrElse[T](fieldName: String, v: T)(implicit jr: JsonReader[T]): T =
      obj.fields.get(fieldName).map(_.convertTo[T]).getOrElse(v)

    def getOpt[T](fieldName: String)(implicit jr: JsonReader[Option[T]]): Option[T] =
      obj.fields.get(fieldName).flatMap(_.convertTo[Option[T]])

    def getList[T](fieldName: String)(implicit jr: JsonReader[T]): List[T] =
      obj.fields.get(fieldName) match {
        case Some(JsArray(l)) ⇒ l.map(_.convertTo[T]).toList
        case _                ⇒ List.empty
      }
  }

  implicit object UuidFormat extends JsonFormat[UUID] {
    def write(u: UUID) = JsString(u.toString)

    def read(v: JsValue) = v match {
      case JsString(s) ⇒ UUID.fromString(s)
      case _ ⇒
        throw new DeserializationException("invalid UUID")
    }
  }

  implicit object LocalDateTimeFormat extends JsonFormat[LocalDateTime] {
    def write(d: LocalDateTime) = JsString(d.toString)

    def read(v: JsValue) = v match {
      case JsString(v) ⇒ LocalDateTime.parse(v)
      case JsNumber(n) ⇒
        Instant.ofEpochSecond(n.toLong)
          .atZone(ZoneId.systemDefault())
          .toLocalDateTime()
      case _ ⇒
        throw new DeserializationException("invalid LocalDateTime")
    }
  }

  implicit object ZonedDateTimeFormat extends JsonFormat[ZonedDateTime] {
    def write(d: ZonedDateTime) =
      JsString(d.withFixedOffsetZone().toString)

    def read(v: JsValue) = v match {
      case JsString(v) ⇒ ZonedDateTime.parse(v)
      case JsNumber(n) ⇒
        Instant.ofEpochSecond(n.toLong)
          .atZone(ZoneId.systemDefault())
          .withFixedOffsetZone()
      case _ ⇒
        throw new DeserializationException("invalid ZonedDateTime")
    }
  }

  def createStringEnumJsonFormat[T <: Enumeration](obj: T) =
    new JsonFormat[T#Value] {
      def write(obj: T#Value) = JsString(obj.toString)

      private val values = OpenHashMap(
        obj.values.toSeq.map(v ⇒ (v.toString.toLowerCase, v)): _*
      )

      private val klassName =
        obj.getClass.getName.split('.').last.dropRight(1).replaceAll("\\$", "#")

      def read(v: JsValue) = {
        val value =
          if (v.isInstanceOf[JsString])
            values.get(v.asInstanceOf[JsString].value.toLowerCase)
          else None
        value.getOrElse(throw new DeserializationException(s"invalid $klassName value"))
      }
    }

  def createIntEnumJsonFormat[T <: Enumeration](obj: T) =
    new JsonFormat[T#Value] {
      def write(obj: T#Value) = JsNumber(obj.id)

      private val values = IntMap(
        obj.values.toSeq.map(v ⇒ (v.id, v)): _*
      )

      private val klassName =
        obj.getClass.getName.split('.').last.dropRight(1).replaceAll("\\$", "#")

      def read(v: JsValue) = {
        val value =
          if (v.isInstanceOf[JsNumber])
            values.get(v.asInstanceOf[JsNumber].value.toInt)
          else None
        value.getOrElse(throw new DeserializationException(s"invalid $klassName value"))
      }
    }

  implicit val paginationDataFormat = jsonFormat3(PaginationData)

  implicit def multipleDataResponse[A: JsonFormat] =
    jsonFormat2(MultipleDataResponse.apply[A])

  // implicit val methodKindJsonProtocol = createStringEnumJsonFormat(MethodKind)

  implicit val resetPasswordRequestJsonProtocol = jsonFormat1(ResetPasswordRequest)

  implicit val resetPasswordSetRequestJsonProtocol = jsonFormat2(ResetPasswordSetRequest)

  implicit val changePasswordRequestJsonProtocol = jsonFormat2(ChangePasswordRequest)

  implicit val userSignUpRequestJsonProtocol = jsonFormat4(UserSignUpRequest)

  implicit val userRequestJsonProtocol = jsonFormat3(UserRequest)

  implicit val userProfileResponseJsonProtocol = jsonFormat4(UserProfileResponse)

  implicit val sessionRequestJsonProtocol = jsonFormat2(SessionRequest)

  implicit val sessionResponseJsonProtocol = jsonFormat1(SessionResponse)

  implicit val nodeStateJsonProtocol =
    createStringEnumJsonFormat(NodeState)

  implicit val agetnNodeResponseJsonProtocol =
    jsonFormat5(AgentNodeResponse)

  // implicit val combRequestJsonProtocol = jsonFormat2(CombRequest)

  // implicit val combResponseJsonProtocol = jsonFormat5(CombResponse)

  // implicit val combMethodRequestJsonProtocol = jsonFormat3(CombMethodRequest)

  // implicit val combMethodResponseJsonProtocol = jsonFormat7(CombMethodResponse)

  implicit val nodeJoinRequestJsonProtocol =
    jsonFormat1(NodeJoinRequest)

  implicit val tokenRoleJsonProtocol =
    createStringEnumJsonFormat(TokenRole)

  implicit val tokenStateJsonProtocol =
    createStringEnumJsonFormat(TokenState)

  implicit val networkPortJsonProtocol =
    createStringEnumJsonFormat(NetworkPort)

  implicit val dockerDeployPortJsonProtocol =
    jsonFormat5(DockerDeployPort)

  implicit val userTokenJsonProtocol = jsonFormat6(UserToken)

  object errors {
    implicit val errorKindFormat = createStringEnumJsonFormat(ErrorKind)

    implicit val errorMessageFormat = jsonFormat4(ErrorMessage)

    implicit val failureResponseFormat = jsonFormat1(FailureResponse.apply)
  }
}
