package io.fcomb.docker.api.json

import ContainerMethodsFormat.{graphDriverDataFormat, RunConfigFormat}
import io.fcomb.docker.api.methods.ContainerMethods.{GraphDriverData, RunConfig}
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

  implicit object ImageInspectFormat extends RootJsonReader[ImageInspect] {
    def read(v: JsValue) = v match {
      case obj: JsObject => ImageInspect(
        id = obj.get[String]("Id"),
        repositoryTags = obj.getList[String]("RepoTags"),
        repositoryDigests = obj.getList[String]("RepoDigests"),
        parentId = obj.getOpt[String]("Parent"),
        comment = obj.getOpt[String]("Comment"),
        createdAt = obj.get[ZonedDateTime]("Created"),
        containerId = obj.getOpt[String]("Container"),
        containerConfig = obj.get[RunConfig]("ContainerConfig"),
        dockerVersion = obj.get[String]("DockerVersion"),
        author = obj.getOpt[String]("Author"),
        config = obj.get[RunConfig]("Config"),
        architecture = obj.get[String]("Architecture"),
        os = obj.get[String]("Os"),
        size = obj.getOpt[Long]("Size")(ZeroOptLongFormat),
        virtualSize = obj.get[Long]("VirtualSize"),
        graphDriver = obj.get[GraphDriverData]("GraphDriver")
      )
      case x => deserializationError(s"Expected JsObject, but got $x")
    }
  }

  implicit object ImageHistoryFormat extends RootJsonFormat[ImageHistory] {
    def read(v: JsValue) = v match {
      case obj: JsObject => ImageHistory(
        id = obj.get[String]("Id"),
        createdAt = obj.get[LocalDateTime]("Created"),
        createdBy = obj.get[String]("CreatedBy"),
        tags = obj.getList[String]("Tags"),
        size = obj.getOpt[Long]("Size")(ZeroOptLongFormat),
        comment = obj.getOpt[String]("Comment")
      )
      case x => deserializationError(s"Expected JsObject, but got $x")
    }

    def write(m: ImageHistory) = throw new NotImplementedError
  }

  implicit val imageSearchResultFormat =
    jsonFormat(ImageSearchResult, "description", "is_official", "is_automated",
      "name", "star_count")
}
