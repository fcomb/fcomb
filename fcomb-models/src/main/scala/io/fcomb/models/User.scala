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
