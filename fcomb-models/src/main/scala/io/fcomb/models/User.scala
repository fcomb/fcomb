package io.fcomb.models

import org.joda.time.DateTime
import com.github.t3hnar.bcrypt._

case class User
(
  id: Option[Int],
  username: String,
  email: Option[String],
  fullName: Option[String],
  passwordHash: String,
  salt: String,
  createdAt: DateTime,
  updatedAt: DateTime
) {
  def isValidPassword(password: String) =
    password.isBcrypted(passwordHash)
}
