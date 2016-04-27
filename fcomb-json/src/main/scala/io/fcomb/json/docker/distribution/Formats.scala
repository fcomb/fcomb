package io.fcomb.json.docker.distribution

import enumeratum.Circe
import io.circe.generic.auto._
import io.circe.{Decoder, Encoder, Json}
import io.fcomb.models.errors.ErrorKind
import io.fcomb.models.errors.docker.distribution._
// import io.fcomb.models.docker.distribution.Manifest

object Formats {
  //   implicit val manifestDecoder = Decoder[Manifest]
  //   implicit val manifestEncoder = Encoder[Manifest]

  implicit val encodeErrorKind = new Encoder[ErrorKind.ErrorKind] {
    def apply(kind: ErrorKind.ErrorKind) =
      Encoder[String].apply(kind.toString)
  }

  implicit val encodeDistributionError = new Encoder[DistributionError] {
    def apply(error: DistributionError) = Json.obj(
      "code" → Encoder[String].apply(error.code.entryName),
      "message" → Encoder[String].apply(error.message)
    )
  }

  implicit val decodeDistributionErrorCode = Circe.decoder(DistributionErrorCode)

  implicit final val decodeInstant: Decoder[DistributionError] =
    Decoder.instance { c =>
      c.get[DistributionErrorCode]("code").flatMap {
        case DistributionErrorCode.DigestInvalid =>
          Decoder[DistributionError.DigestInvalid].apply(c)
      }
    }
}
