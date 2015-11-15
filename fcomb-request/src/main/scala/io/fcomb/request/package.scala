package io.fcomb

import io.fcomb.models.comb._

package object request {
  trait ServiceModelRequest

  case class UserSignUpRequest(
    email: String,
    password: String,
    username: String,
    fullName: Option[String]
  ) extends ServiceModelRequest

  case class UserRequest(
    email: String,
    username: String,
    fullName: Option[String]
  ) extends ServiceModelRequest

  case class ResetPasswordRequest(
    email: String
  ) extends ServiceModelRequest

  case class ResetPasswordSetRequest(
    token: String,
    password: String
  ) extends ServiceModelRequest

  case class ChangePasswordRequest(
    oldPassword: String,
    newPassword: String
  ) extends ServiceModelRequest

  case class CombRequest(
    name: String,
    slug: Option[String]
  ) extends ServiceModelRequest

  case class CombMethodRequest(
    kind: MethodKind.MethodKind,
    uri: String,
    endpoint: String
  ) extends ServiceModelRequest

  case class SessionRequest(
    email: String,
    password: String
  ) extends ServiceModelRequest
}
