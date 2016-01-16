package io.fcomb

import io.fcomb.models._, application._, node._, docker._

package object request {
  trait ServiceModelRequest extends models.ServiceModel

  case class UserSignUpRequest(
    email:    String,
    password: String,
    username: String,
    fullName: Option[String]
  ) extends ServiceModelRequest

  case class UserRequest(
    email:    String,
    username: String,
    fullName: Option[String]
  ) extends ServiceModelRequest

  case class ResetPasswordRequest(
    email: String
  ) extends ServiceModelRequest

  case class ResetPasswordSetRequest(
    token:    String,
    password: String
  ) extends ServiceModelRequest

  case class ChangePasswordRequest(
    oldPassword: String,
    newPassword: String
  ) extends ServiceModelRequest

  // case class CombRequest(
  //   name: String,
  //   slug: Option[String]
  // ) extends ServiceModelRequest

  // case class CombMethodRequest(
  //   kind: MethodKind.MethodKind,
  //   uri: String,
  //   endpoint: String
  // ) extends ServiceModelRequest

  case class SessionRequest(
    email:    String,
    password: String
  ) extends ServiceModelRequest

  // TODO: signed datetime with private key with HMAC?
  case class NodeJoinRequest(
    certificationRequest: String
  ) extends ServiceModelRequest

  case class ApplicationRequest(
    name:          String,
    image:         DockerImage,
    deployOptions: DockerDeployOptions,
    scaleStrategy: ScaleStrategy
  ) extends ServiceModelRequest
}
