package io.fcomb.docker.api

package object methods {
  sealed trait DockerApiMethod

  trait DockerApiResponse extends DockerApiMethod

  trait DockerApiRequest extends DockerApiMethod

  sealed trait DockerApiException extends Throwable {
    val msg: String
  }

  case class BadParameterException(msg: String) extends DockerApiException

  case class PermissionDeniedException(msg: String) extends DockerApiException

  case class ServerErrorException(msg: String) extends DockerApiException

  case class ResouceOrContainerNotFoundException(msg: String) extends DockerApiException

  case class ImpossibleToAttachException(msg: String) extends DockerApiException

  case class UnknownException(msg: String) extends DockerApiException

  case class ConflictException(msg: String) extends DockerApiException

  trait MapToString {
    def mapToString(): String
  }
}
