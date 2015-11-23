package io.fcomb.docker.api.json

import io.fcomb.docker.api.methods.ImageMethods._
import spray.json._
import spray.json.DefaultJsonProtocol.{listFormat => _, _}
import io.fcomb.json._
import java.time.{LocalDateTime, ZonedDateTime}

private[api] object ImageMethodsFormat {
  implicit val imageItemFormat =
    jsonFormat(ImageItem, "Id", "ParentId", "RepoTags", "RepoDigests",
      "Created", "Size", "VirtualSize", "Labels")

  implicit val registryConfigItemFormat = jsonFormat2(RegistryConfigItem)

  implicit object RegistryConfigFormat extends RootJsonWriter[RegistryConfig] {
    def write(c: RegistryConfig) = JsObject(c.map {
      case (k, v) => k.toString -> v.toJson
    })
  }

  implicit val authConfigFormat =
    jsonFormat(AuthConfig.apply, "username", "password",
      "email", "serveraddress")
}
