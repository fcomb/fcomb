package io.fcomb.models

case class Session(token: String)

case class SessionCreateRequest(
  email:    String,
  password: String
)
