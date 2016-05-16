package io.fcomb.json.docker.distribution

import cats.data.Xor
import enumeratum.Circe
import io.circe.generic.auto._
import io.circe.java8.time._
import io.circe.parser._
import io.circe.{Decoder, Encoder, ParsingFailure, DecodingFailure, Json, Printer}
import io.fcomb.models.docker.distribution._
import io.fcomb.models.errors.ErrorKind
import io.fcomb.models.errors.docker.distribution._
import java.time.ZonedDateTime
import java.util.Base64

object Formats {
  private final val compactPrinter: Printer = Printer(
    preserveOrder = true,
    dropNullKeys = true,
    indent = ""
  )

  implicit final val encodeErrorKind = new Encoder[ErrorKind.ErrorKind] {
    def apply(kind: ErrorKind.ErrorKind) =
      Encoder[String].apply(kind.toString)
  }

  implicit final val encodeDistributionError: Encoder[DistributionError] =
    Encoder.forProduct2("code", "message")(e ⇒ (e.code.entryName, e.message))

  private final val encodeSchemaV1Config: Encoder[SchemaV1.Config] =
    Encoder.forProduct12("id", "parent", "comment", "created", "container", "container_config",
      "docker_version", "author", "config", "architecture", "os", "Size")(SchemaV1.Config.unapply(_).get)

  private final val encodeSchemaV1Layer: Encoder[SchemaV1.Layer] =
    Encoder.forProduct7("id", "parent", "comment", "created", "container_config",
      "author", "throwaway")(SchemaV1.Layer.unapply(_).get)

  implicit final val encodeSchemaV1FsLayer: Encoder[SchemaV1.FsLayer] =
    Encoder.forProduct1("blobSum")(SchemaV1.FsLayer.unapply(_).get)

  implicit final val encodeSchemaV1Compatibility = new Encoder[SchemaV1.Compatibility] {
    def apply(compatibility: SchemaV1.Compatibility) = {
      val layerJson = compatibility match {
        case c: SchemaV1.Config => encodeSchemaV1Config.apply(c)
        case l: SchemaV1.Layer => encodeSchemaV1Layer.apply(l)
      }
      Json.obj(
        "v1Compatibility" → Encoder[String].apply(compactPrinter.pretty(layerJson))
      )
    }
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

  implicit final val decodeSchemaV1LayerContainerConfig: Decoder[SchemaV1.LayerContainerConfig] =
    Decoder.forProduct1("Cmd")(SchemaV1.LayerContainerConfig.apply)

  private final val decodeSchemaV1Layer =
    Decoder.forProduct7("id", "parent", "comment", "created", "container_config",
      "author", "throwaway")(SchemaV1.Layer.apply)

  implicit final val decodeSchemaV1LayerFromV1Compatibility: Decoder[SchemaV1.Layer] =
    Decoder.instance { c ⇒
      c.get[String]("v1Compatibility").flatMap { cs ⇒
        parse(cs).map(_.hcursor) match {
          case Xor.Right(hc)                    ⇒ decodeSchemaV1Layer(hc)
          case Xor.Left(ParsingFailure(msg, _)) ⇒ Xor.left(DecodingFailure(msg, Nil))
        }
      }
    }

  implicit final val decodeSchemaV1ContainerConfig: Decoder[SchemaV1.ContainerConfig] =
    Decoder.forProduct22("Hostname", "Domainname", "User", "AttachStdin", "AttachStdout",
      "AttachStderr", "ExposedPorts", "Tty", "OpenStdin", "StdinOnce", "Env", "Cmd", "ArgsEscaped",
      "Image", "Volumes", "WorkingDir", "Entrypoint", "NetworkDisabled", "MacAddress",
      "OnBuild", "Labels", "StopSignal")(SchemaV1.ContainerConfig.apply)

  private final val decodeSchemaV1Config: Decoder[SchemaV1.Config] =
    Decoder.forProduct12("id", "parent", "comment", "created", "container", "container_config",
      "docker_version", "author", "config", "architecture", "os", "Size")(SchemaV1.Config.apply)

  implicit final val decodeSchemaV1ConfigFromV1Compatibility: Decoder[SchemaV1.Config] =
    Decoder.instance { c ⇒
      c.get[String]("v1Compatibility").flatMap { cs ⇒
        parse(cs).map(_.hcursor) match {
          case Xor.Right(hc)                    ⇒ decodeSchemaV1Config(hc)
          case Xor.Left(ParsingFailure(msg, _)) ⇒ Xor.left(DecodingFailure(msg, Nil))
        }
      }
    }

  implicit final val decodeSchemaV1CompatibilityList: Decoder[List[SchemaV1.Compatibility]] =
    Decoder.instance { c ⇒
      c.focus.asArray match {
        case Some(configJson :: xsJson) ⇒
          for {
            config ← configJson.as[SchemaV1.Config]
            acc = Xor.right[DecodingFailure, List[SchemaV1.Layer]](Nil)
            xs ← xsJson.foldRight(acc) { (item, lacc) ⇒
              for {
                acc ← lacc
                layer ← item.as[SchemaV1.Layer]
              } yield layer :: acc
            }
          } yield config :: xs
        case _ ⇒ Xor.left(DecodingFailure("history", c.history))
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

  implicit final val decodeSchemaV2ImageRootFs: Decoder[SchemaV2.ImageRootFs] =
    Decoder.forProduct3("type", "diff_ids", "base_layer")(SchemaV2.ImageRootFs.apply)

  implicit final val decodeSchemaV2ImageHistory: Decoder[SchemaV2.ImageHistory] =
    Decoder.forProduct5("created", "author", "created_by", "comment", "empty_layer")(SchemaV2.ImageHistory.apply)

  implicit final val decodeSchemaV2ImageConfig: Decoder[SchemaV2.ImageConfig] =
    Decoder.forProduct3("rootfs", "history", "architecture")(SchemaV2.ImageConfig.apply)
}
