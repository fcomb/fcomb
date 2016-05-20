package io.fcomb.docker.api

import spray.json._
import org.apache.commons.codec.binary.Base64

package object methods {
  sealed trait DockerApiMethod
  trait DockerApiResponse extends DockerApiMethod
  trait DockerApiRequest extends DockerApiMethod

  abstract class DockerApiException(msg: String) extends Throwable(msg)
  case class BadParameterException(msg: String) extends DockerApiException(msg)
  case class PermissionDeniedException(msg: String)
    extends DockerApiException(msg)
  case class ServerErrorException(msg: String) extends DockerApiException(msg)
  case class ResouceOrContainerNotFoundException(msg: String)
    extends DockerApiException(msg)
  case class ImpossibleToAttachException(msg: String)
    extends DockerApiException(msg)
  case class UnknownException(msg: String) extends DockerApiException(msg)
  case class ConflictException(msg: String) extends DockerApiException(msg)

  trait MapToString {
    def mapToString(): String
  }

  private[api] def mapToJsonAsBase64[T](obj: T)(implicit jw: JsonWriter[T]) =
    Base64.encodeBase64String(obj.toJson.compactPrint.getBytes)
}
