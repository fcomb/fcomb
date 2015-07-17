package io.fcomb.models

import java.time.LocalDateTime
import com.github.t3hnar.bcrypt._
import java.util.UUID
import scalaz._

case class User(
    id:           UUID,
    email:        String,
    username:     String,
    fullName:     Option[String],
    passwordHash: String,
    createdAt:    LocalDateTime,
    updatedAt:    LocalDateTime
) extends ModelWithUuid {
  def isValidPassword(password: String) =
    password.isBcrypted(passwordHash)
}

// TODO
trait ApiServiceRequest

// TODO
trait ApiServiceResponse

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
  id:       java.util.UUID,
  email:    String,
  username: String,
  fullName: Option[String]
) extends ApiServiceResponse

case class ValidationErrorsResponse(
  errors: Map[String, List[String]]
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
}
