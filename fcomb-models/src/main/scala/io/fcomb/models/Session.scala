package io.fcomb.models

case class Session(
  token: String
)

case class SessionRequest(
  email: String,
  password: String
) extends ModelServiceRequest

case class SessionResponse(
  token: String
) extends ModelServiceResponse
