package io.fcomb.models

case class Session(
  token: String
)

case class SessionRequest(
  email: String,
  password: String
) extends ApiServiceRequest

case class SessionResponse(
  token: String
) extends ApiServiceResponse
