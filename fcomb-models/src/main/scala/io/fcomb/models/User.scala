package io.fcomb.models

import java.time.LocalDateTime
import com.github.t3hnar.bcrypt._
import java.util.UUID

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

trait ApiServiceRequest

trait ApiServiceResponse

case class UserRequest(
  email:    String,
  password: String,
  username: String,
  fullName: Option[String]
) extends ApiServiceRequest

case class UserResponse(
  id:       java.util.UUID,
  email:    String,
  username: String,
  fullName: Option[String]
) extends ApiServiceResponse
