package io.fcomb.models

import org.joda.time.DateTime

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
)
