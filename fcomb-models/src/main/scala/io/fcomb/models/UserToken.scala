package io.fcomb.models

import java.time.ZonedDateTime

object TokenRole extends Enumeration {
  type TokenRole = Value

  val JoinCluster = Value("join_cluster")
  val Api = Value("api")
}

object TokenState extends Enumeration {
  type TokenState = Value

  val Enabled = Value("enabled")
  val Disabled = Value("disabled")
}

sealed trait Token {
  val token: String
  val role: TokenRole.TokenRole
  val state: TokenState.TokenState
  val createdAt: ZonedDateTime
  val updatedAt: ZonedDateTime
}

case class UserToken(
  token: String,
  role: TokenRole.TokenRole,
  state: TokenState.TokenState,
  userId: Long,
  createdAt: ZonedDateTime,
  updatedAt: ZonedDateTime
)
