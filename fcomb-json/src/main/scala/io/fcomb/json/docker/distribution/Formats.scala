package io.fcomb.json.docker.distribution

import cats.data.Xor
import enumeratum.Circe
import io.circe.generic.auto._
import io.circe.java8.time._
import io.circe.parser._
import io.circe.{Decoder, Encoder, Json, ParsingFailure, DecodingFailure}
import io.fcomb.models.docker.distribution._
import io.fcomb.models.errors.ErrorKind
import io.fcomb.models.errors.docker.distribution._
import java.time.ZonedDateTime
import java.util.Base64

object Formats {
  implicit final val encodeErrorKind = new Encoder[ErrorKind.ErrorKind] {
    def apply(kind: ErrorKind.ErrorKind) =
      Encoder[String].apply(kind.toString)
  }

  implicit final val encodeDistributionError = new Encoder[DistributionError] {
    def apply(error: DistributionError) = Json.obj(
      "code" → Encoder[String].apply(error.code.entryName),
      "message" → Encoder[String].apply(error.message)
    )
  }

  implicit final val decodeDistributionErrorCode =
    Circe.decoder(DistributionErrorCode)

  implicit final val decodeDistributionError: Decoder[DistributionError] =
    Decoder.instance { c ⇒
      c.get[DistributionErrorCode]("code").flatMap {
        case DistributionErrorCode.DigestInvalid ⇒
          Decoder[DistributionError.DigestInvalid].apply(c)
        case DistributionErrorCode.Unknown ⇒
          Decoder[DistributionError.Unknown].apply(c)
        case DistributionErrorCode.NameInvalid ⇒
          Decoder[DistributionError.NameInvalid].apply(c)
      }
    }

  implicit final val decodeContainerConfig: Decoder[SchemaV1.ContainerConfig] =
    Decoder.instance { c ⇒
      c.get[List[String]]("Cmd").map(SchemaV1.ContainerConfig(_))
    }

  implicit final val decodeV1Compatibility: Decoder[SchemaV1.V1Compatibility] =
    Decoder.instance { c ⇒
      c.get[String]("v1Compatibility").flatMap { compS ⇒
        parse(compS).map(_.hcursor) match {
          case Xor.Right(hc) ⇒
            for {
              id ← hc.get[String]("id")
              parent ← hc.get[Option[String]]("parent")
              comment ← hc.get[Option[String]]("comment")
              containerConfig ← hc.get[Option[SchemaV1.ContainerConfig]]("container_config")
              author ← hc.get[Option[String]]("author")
              throwAway ← hc.get[Option[Boolean]]("throw_away")
            } yield SchemaV1.V1Compatibility(
              id = id,
              parent = parent,
              comment = comment,
              containerConfig = containerConfig,
              author = author,
              throwAway = throwAway
            )
          case Xor.Left(ParsingFailure(msg, _)) ⇒
            Xor.left(DecodingFailure(msg, Nil))
        }
      }
    }

  implicit final val decodeSchemaManifest: Decoder[SchemaManifest] =
    Decoder.instance { c ⇒
      c.get[Int]("schemaVersion").flatMap {
        case 1 ⇒ Decoder[SchemaV1.Manifest].apply(c)
        case 2 ⇒ Decoder[SchemaV2.Manifest].apply(c)
        case _ ⇒ Xor.left(DecodingFailure("Unsupported schemaVersion", c.history))
      }
    }

  implicit final val decodeSchemaV1Protected: Decoder[SchemaV1.Protected] =
    Decoder.instance { c ⇒
      for {
        formatLength ← c.get[Int]("formatLength")
        formatTail ← c.get[String]("formatTail")
        time ← c.get[ZonedDateTime]("time")
      } yield SchemaV1.Protected(
        formatLength = formatLength,
        formatTail = new String(Base64.getDecoder.decode(formatTail)),
        time = time
      )
    }
}
