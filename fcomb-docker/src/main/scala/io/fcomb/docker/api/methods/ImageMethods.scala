package io.fcomb.docker.api.methods

import akka.http.scaladsl.model.headers.RawHeader
import scala.collection.immutable
import spray.json._
import org.apache.commons.codec.binary.Base64
import java.net.URL
import java.time.{LocalDateTime, ZonedDateTime}

object ImageMethods {
  final case class ImageItem(
    id: String,
    parentId: Option[String],
    repoTags: List[String],
    repoDigests: List[String],
    createdAt: ZonedDateTime,
    size: Long,
    virtualSize: Long,
    labels: Map[String, String]
  ) extends DockerApiResponse

  object RemoveMode extends Enumeration {
    type RemoveMode = Value

    val No, Default, Force = Value

    def mapToParams(mode: RemoveMode) = mode match {
      case No => Map.empty
      case Default => Map("rm" -> "true")
      case Force => Map("forcerm" -> "true")
    }
  }

  type RegistryConfig = Map[URL, RegistryConfigItem]

  final case class RegistryConfigItem(
    username: String,
    password: String
  )

  private def mapToJsonAsBase64[T](obj: T)(implicit jw: JsonWriter[T]) =
    Base64.encodeBase64String(obj.toJson.compactPrint.getBytes)

  object RegistryConfig {
    import io.fcomb.docker.api.json.ImageMethodsFormat.RegistryConfigFormat

    def mapToHeaders(configOpt: Option[RegistryConfig]) =
      configOpt match {
        case Some(config) =>
          val value = mapToJsonAsBase64(config)
          immutable.Seq(RawHeader("X-Registry-Config", value))
        case None =>
          immutable.Seq.empty
      }
  }

  final case class AuthConfig(
    username: String,
    password: String,
    email: Option[String],
    serverAddress: String
  ) extends DockerApiResponse

  object AuthConfig {
    import io.fcomb.docker.api.json.ImageMethodsFormat.authConfigFormat

    def mapToHeaders(configOpt: Option[AuthConfig]) =
      configOpt match {
        case Some(config) =>
          val value = mapToJsonAsBase64(config)
          immutable.Seq(RawHeader("X-Registry-Auth", value))
        case None =>
          immutable.Seq.empty
      }
  }
}
