package io.fcomb.models

import comb._
import java.time.LocalDateTime
import com.github.t3hnar.bcrypt._
import java.util.UUID
import scalaz._

case class User(
    id:           Option[UUID] = None,
    email:        String,
    username:     String,
    fullName:     Option[String],
    passwordHash: String,
    createdAt:    LocalDateTime,
    updatedAt:    LocalDateTime
) extends ModelWithUuidPk {
  def isValidPassword(password: String) =
    password.isBcrypted(passwordHash)
}

// TODO
trait ApiServiceRequest

// TODO
trait ApiServiceResponse

case class NoContentResponse() extends ApiServiceResponse

case class UserSignUpRequest(
  email:    String,
  password: String,
  username: String,
  fullName: Option[String]
) extends ApiServiceRequest

case class UserRequest(
  email:    String,
  username: String,
  fullName: Option[String]
) extends ApiServiceRequest

case class UserResponse(
  id:       Option[UUID],
  email:    String,
  username: String,
  fullName: Option[String]
) extends ApiServiceResponse

case class ResetPasswordRequest(
  email: String
) extends ApiServiceRequest

case class ResetPasswordSetRequest(
  token:    String,
  password: String
) extends ApiServiceRequest

case class ChangePasswordRequest(
  oldPassword: String,
  newPassword: String
) extends ApiServiceRequest

case class ValidationErrorsResponse(
  errors: Map[String, List[String]]
) extends ApiServiceResponse

case class CombRequest(
  name: String,
  slug: Option[String]
) extends ApiServiceRequest

case class CombResponse(
  id:        Option[Long],
  name:      String,
  slug:      String,
  createdAt: LocalDateTime,
  updatedAt: LocalDateTime
) extends ApiServiceResponse

case class CombMethodRequest(
  kind:     MethodKind.MethodKind,
  uri:      String,
  endpoint: String
) extends ApiServiceRequest

case class CombMethodResponse(
  id:        Option[Long],
  combId:    Long,
  kind:      MethodKind.MethodKind,
  uri:       String,
  endpoint:  String,
  createdAt: LocalDateTime,
  updatedAt: LocalDateTime
) extends ApiServiceResponse

import scala.language.implicitConversions
object ResponseConversions {
  implicit def user2Response(u: User): UserResponse =
    UserResponse(
      id = u.id,
      email = u.email,
      username = u.username,
      fullName = u.fullName
    )

  implicit def session2Response(s: Session): SessionResponse =
    SessionResponse(s.token)

  implicit def comb2Response(c: comb.Comb): CombResponse =
    CombResponse(
      id = c.id,
      name = c.name,
      slug = c.slug,
      createdAt = c.createdAt,
      updatedAt = c.updatedAt
    )

  implicit def combMethod2Response(m: comb.CombMethod): CombMethodResponse =
    CombMethodResponse(
      id = m.id,
      combId = m.combId,
      kind = m.kind,
      uri = m.uri,
      endpoint = m.endpoint,
      createdAt = m.createdAt,
      updatedAt = m.updatedAt
    )
}
