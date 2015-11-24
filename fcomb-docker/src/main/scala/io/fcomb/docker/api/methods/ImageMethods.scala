package io.fcomb.docker.api.methods

import ContainerMethods.{GraphDriverData, RunConfig}
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
    repositoryTags: List[String],
    repositoryDigests: List[String],
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

    private val emptyConfig = Base64.encodeBase64String(JsObject().compactPrint.getBytes)

    def mapToHeaders(configOpt: Option[AuthConfig]) = {
      val value = configOpt match {
        case Some(config) => mapToJsonAsBase64(config)
        case None => emptyConfig
      }
      immutable.Seq(RawHeader("X-Registry-Auth", value))
    }
  }

  final case class ImageInspect(
    id: String,
    repositoryTags: List[String],
    repositoryDigests: List[String],
    parentId: Option[String],
    comment: Option[String],
    createdAt: ZonedDateTime,
    containerId: Option[String],
    containerConfig: RunConfig,
    dockerVersion: String,
    author: Option[String],
    config: RunConfig,
    architecture: String,
    os: String,
    size: Option[Long],
    virtualSize: Long,
    graphDriver: GraphDriverData
  ) extends DockerApiResponse

  final case class ImageHistory(
    id: String,
    createdAt: LocalDateTime,
    createdBy: String,
    tags: List[String],
    size: Option[Long],
    comment: Option[String]
  )

  final case class ImageDelete(
    untagged: String,
    deleted: String
  )

  final case class Image(
    id: String,
    parentId: String,
    repositoryTags: List[String],
    repositoryDigests: List[String],
    created: Int,
    size: Int,
    virtualSize: Int,
    labels: Map[String, String]
  )

  final case class Registry(name: String) {
    val url =
      if (name.toLowerCase.startsWith("http")) new URL(name)
      else new URL(s"http://$name")

    def toParam() =
      if (url.getPort == -1) url.getHost()
      else s"${url.getHost}:${url.getPort}"
  }

  final case class ImageSearchResult(
    description: Option[String],
    isOfficial: Boolean,
    isAutomated: Boolean,
    name: String,
    starCount: Int
  )
}
