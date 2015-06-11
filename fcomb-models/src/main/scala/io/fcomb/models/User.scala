package io.fcomb.models

import org.joda.time.DateTime
import com.github.t3hnar.bcrypt._
import java.util.UUID

case class User
(
  id: UUID,
  email: String,
  username: String,
  fullName: Option[String],
  passwordHash: String,
  createdAt: DateTime,
  updatedAt: DateTime
) extends ModelWithUuid {
  def isValidPassword(password: String) =
    password.isBcrypted(passwordHash)
}
