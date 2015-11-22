package io.fcomb.docker.api.methods

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
}
