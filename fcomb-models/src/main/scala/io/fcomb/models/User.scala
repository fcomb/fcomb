package io.fcomb.models

import com.github.t3hnar.bcrypt._
import java.time.ZonedDateTime

final case class User(
    id:           Option[Long]          = None,
    email:        String,
    username:     String,
    fullName:     Option[String],
    passwordHash: String,
    createdAt:    ZonedDateTime,
    updatedAt:    Option[ZonedDateTime]
) extends ModelWithAutoLongPk {
  def withPk(id: Long) = this.copy(id = Some(id))

  def isValidPassword(password: String) =
    password.isBcrypted(passwordHash)
}

final case class UserSignUpRequest(
  email:    String,
  password: String,
  username: String,
  fullName: Option[String]
)

final case class UserUpdateRequest(
  email:    String,
  username: String,
  fullName: Option[String]
)
