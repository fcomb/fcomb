package io.fcomb.models

import io.fcomb.models.comb._

package object request {
  trait ModelServiceRequest

  case class UserSignUpRequest(
    email: String,
    password: String,
    username: String,
    fullName: Option[String]
  ) extends ModelServiceRequest

  case class UserRequest(
    email: String,
    username: String,
    fullName: Option[String]
  ) extends ModelServiceRequest

  case class ResetPasswordRequest(
    email: String
  ) extends ModelServiceRequest

  case class ResetPasswordSetRequest(
    token: String,
    password: String
  ) extends ModelServiceRequest

  case class ChangePasswordRequest(
    oldPassword: String,
    newPassword: String
  ) extends ModelServiceRequest

  case class CombRequest(
    name: String,
    slug: Option[String]
  ) extends ModelServiceRequest

  case class CombMethodRequest(
    kind: MethodKind.MethodKind,
    uri: String,
    endpoint: String
  ) extends ModelServiceRequest

  case class SessionRequest(
    email: String,
    password: String
  ) extends ModelServiceRequest
}
