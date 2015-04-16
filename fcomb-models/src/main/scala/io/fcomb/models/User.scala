package io.fcomb.models

import org.joda.time.DateTime

case class User
(
  id: Option[Int],
  email: String,
  fullName: Option[String],
  phoneNumber: Option[String],
  passwordHash: String,
  salt: String,
  createdAt: DateTime,
  updatedAt: DateTime
)
