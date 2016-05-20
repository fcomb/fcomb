package io.fcomb.models

import com.github.t3hnar.bcrypt._
import java.time.ZonedDateTime

@SerialVersionUID(1L)
case class User(
  id:           Option[Long]   = None,
  email:        String,
  username:     String,
  fullName:     Option[String],
  passwordHash: String,
  createdAt:    ZonedDateTime,
  updatedAt:    ZonedDateTime
)
    extends ModelWithAutoLongPk {
  def withPk(id: Long) = this.copy(id = Some(id))

  def isValidPassword(password: String) =
    password.isBcrypted(passwordHash)
}
