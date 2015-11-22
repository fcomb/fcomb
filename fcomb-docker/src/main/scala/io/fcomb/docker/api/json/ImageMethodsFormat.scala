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

}
