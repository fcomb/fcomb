package io.fcomb.json.docker.distribution

import io.circe.generic.auto._
import io.circe.{Decoder, Encoder, Json}
// import io.fcomb.models.docker.distribution.Manifest
import io.fcomb.models.errors.docker.distribution.DistributionError

object Formats {
  //   implicit val manifestDecoder = Decoder[Manifest]
  //   implicit val manifestEncoder = Encoder[Manifest]

  implicit val encodeDistributionError = new Encoder[DistributionError] {
    def apply(error: DistributionError) = Json.obj(
      "code" → Encoder[String].apply(error.code.entryName),
      "message" → Encoder[String].apply(error.message)
    )
  }
}
