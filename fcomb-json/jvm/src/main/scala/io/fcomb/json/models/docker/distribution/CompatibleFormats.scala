/*
 * Copyright 2016 fcomb. <https://fcomb.io>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.fcomb.json.models.docker.distribution

import cats.data.Xor
import enumeratum.Circe
import io.circe.generic.semiauto._
import io.circe.java8.time._
import io.circe.parser._
import io.circe.{
  Decoder,
  Encoder,
  ParsingFailure,
  DecodingFailure,
  Json,
  Printer,
  HCursor,
  KeyDecoder
}
import io.fcomb.models.docker.distribution._
import io.fcomb.models.errors.docker.distribution._
import scala.collection.generic.CanBuildFrom

object CompatibleFormats {
  private final val compactPrinter: Printer = Printer(
    preserveOrder = true,
    dropNullKeys = true,
    indent = ""
  )

  implicit final val encodeSchemaV1ContainerConfig: Encoder[SchemaV1.ContainerConfig] =
    Encoder.forProduct22("Hostname",
                         "Domainname",
                         "User",
                         "AttachStdin",
                         "AttachStdout",
                         "AttachStderr",
                         "ExposedPorts",
                         "Tty",
                         "OpenStdin",
                         "StdinOnce",
                         "Env",
                         "Cmd",
                         "ArgsEscaped",
                         "Image",
                         "Volumes",
                         "WorkingDir",
                         "Entrypoint",
                         "NetworkDisabled",
                         "MacAddress",
                         "OnBuild",
                         "Labels",
                         "StopSignal")(SchemaV1.ContainerConfig.unapply(_).get)

  final val encodeSchemaV1Config: Encoder[SchemaV1.Config] =
    Encoder.forProduct13("id",
                         "parent",
                         "comment",
                         "created",
                         "container",
                         "container_config",
                         "docker_version",
                         "author",
                         "config",
                         "architecture",
                         "os",
                         "Size",
                         "throwaway")(SchemaV1.Config.unapply(_).get)

  implicit final val encodeSchemaV1LayerContainerConfig: Encoder[SchemaV1.LayerContainerConfig] =
    Encoder.forProduct1("Cmd")(SchemaV1.LayerContainerConfig.unapply(_).get)

  final val encodeSchemaV1Layer: Encoder[SchemaV1.Layer] = Encoder
    .forProduct7("id", "parent", "comment", "created", "container_config", "author", "throwaway")(
      SchemaV1.Layer.unapply(_).get)

  implicit final val encodeSchemaV1FsLayer: Encoder[SchemaV1.FsLayer] =
    Encoder.forProduct1("blobSum")(SchemaV1.FsLayer.unapply(_).get)

  implicit final val encodeSchemaV1Compatibility = new Encoder[SchemaV1.Compatibility] {
    def apply(compatibility: SchemaV1.Compatibility) = {
      val layerJson = compatibility match {
        case c: SchemaV1.Config => encodeSchemaV1Config.apply(c)
        case l: SchemaV1.Layer  => encodeSchemaV1Layer.apply(l)
      }
      Json.obj(
        "v1Compatibility" -> Encoder[String].apply(compactPrinter.pretty(layerJson))
      )
    }
  }

  implicit final val encodeSignatureHeader: Encoder[SchemaV1.SignatureHeader] = deriveEncoder

  implicit final val encodeSchemaV1Signature: Encoder[SchemaV1.Signature] =
    Encoder.forProduct3("header", "signature", "protected")(SchemaV1.Signature.unapply(_).get)

  implicit final val encodeSchemaV1Manifest: Encoder[SchemaV1.Manifest] =
    Encoder.forProduct7("name",
                        "tag",
                        "fsLayers",
                        "architecture",
                        "history",
                        "signatures",
                        "schemaVersion")(SchemaV1.Manifest.unapply(_).get)

  implicit final val encodeSchemaV1Protected: Encoder[SchemaV1.Protected] =
    Encoder.forProduct3("formatLength", "formatTail", "time")(SchemaV1.Protected.unapply(_).get)

  implicit final val decodeDistributionErrorCode = Circe.decoder(DistributionErrorCode)

  implicit final def decodeCanBuildFromWithNull[A, C[_]](
      implicit d: Decoder[A],
      cbf: CanBuildFrom[Nothing, A, C[A]]
  ): Decoder[C[A]] = new Decoder[C[A]] {
    final def apply(c: HCursor): Decoder.Result[C[A]] = {
      if (c.focus.isNull) Xor.Right(cbf.apply.result)
      else Decoder.decodeCanBuildFrom(d, cbf).apply(c)
    }
  }

  implicit final def decodeMapLikeWithNull[M[K, +V] <: Map[K, V], K, V](
      implicit dk: KeyDecoder[K],
      dv: Decoder[V],
      cbf: CanBuildFrom[Nothing, (K, V), M[K, V]]
  ): Decoder[M[K, V]] = new Decoder[M[K, V]] {
    final def apply(c: HCursor): Decoder.Result[M[K, V]] = {
      if (c.focus.isNull) Xor.Right(cbf.apply.result)
      else Decoder.decodeMapLike(dk, dv, cbf).apply(c)
    }
  }

  implicit final val decodeSchemaV1LayerContainerConfig: Decoder[SchemaV1.LayerContainerConfig] =
    Decoder.forProduct1("Cmd")(SchemaV1.LayerContainerConfig.apply)

  final val decodeSchemaV1Layer = Decoder
    .forProduct7("id", "parent", "comment", "created", "container_config", "author", "throwaway")(
      SchemaV1.Layer.apply)

  implicit final val decodeSchemaV1LayerFromV1Compatibility: Decoder[SchemaV1.Layer] =
    Decoder.instance { c =>
      c.get[String]("v1Compatibility").flatMap { cs =>
        parse(cs).map(_.hcursor) match {
          case Xor.Right(hc)                    => decodeSchemaV1Layer(hc)
          case Xor.Left(ParsingFailure(msg, _)) => Xor.Left(DecodingFailure(msg, Nil))
        }
      }
    }

  implicit final val decodeSchemaV1ContainerConfig: Decoder[SchemaV1.ContainerConfig] =
    Decoder.forProduct22("Hostname",
                         "Domainname",
                         "User",
                         "AttachStdin",
                         "AttachStdout",
                         "AttachStderr",
                         "ExposedPorts",
                         "Tty",
                         "OpenStdin",
                         "StdinOnce",
                         "Env",
                         "Cmd",
                         "ArgsEscaped",
                         "Image",
                         "Volumes",
                         "WorkingDir",
                         "Entrypoint",
                         "NetworkDisabled",
                         "MacAddress",
                         "OnBuild",
                         "Labels",
                         "StopSignal")(SchemaV1.ContainerConfig.apply)

  final val decodeSchemaV1Config: Decoder[SchemaV1.Config] =
    Decoder.forProduct13("id",
                         "parent",
                         "comment",
                         "created",
                         "container",
                         "container_config",
                         "docker_version",
                         "author",
                         "config",
                         "architecture",
                         "os",
                         "Size",
                         "throwaway")(SchemaV1.Config.apply)

  implicit final val decodeSchemaV1ConfigFromV1Compatibility: Decoder[SchemaV1.Config] =
    Decoder.instance { c =>
      c.get[String]("v1Compatibility").flatMap { cs =>
        parse(cs).map(_.hcursor) match {
          case Xor.Right(hc)                    => decodeSchemaV1Config(hc)
          case Xor.Left(ParsingFailure(msg, _)) => Xor.Left(DecodingFailure(msg, Nil))
        }
      }
    }

  implicit final val decodeSchemaV1CompatibilityList: Decoder[List[SchemaV1.Compatibility]] =
    Decoder.instance { c =>
      c.focus.asArray match {
        case Some(configJson :: xsJson) =>
          for {
            config <- configJson.as[SchemaV1.Config]
            acc = Xor.right[DecodingFailure, List[SchemaV1.Layer]](Nil)
            xs <- xsJson.foldRight(acc) { (item, lacc) =>
              for {
                acc   <- lacc
                layer <- item.as[SchemaV1.Layer]
              } yield layer :: acc
            }
          } yield config :: xs
        case _ => Xor.Left(DecodingFailure("history", c.history))
      }
    }

  implicit final val decodeSchemaV1SignatureHeader: Decoder[SchemaV1.SignatureHeader] =
    deriveDecoder

  implicit final val decodeSchemaV1Signature: Decoder[SchemaV1.Signature] = deriveDecoder

  implicit final val decodeSchemaV1FsLayer: Decoder[SchemaV1.FsLayer] = deriveDecoder

  implicit final val decodeSchemaV1Manifest: Decoder[SchemaV1.Manifest] = deriveDecoder

  implicit final val decodeSchemaV2Descriptor: Decoder[SchemaV2.Descriptor] = deriveDecoder

  implicit final val decodeSchemaV2Manifest: Decoder[SchemaV2.Manifest] = deriveDecoder

  implicit final val decodeSchemaManifest: Decoder[SchemaManifest] = Decoder.instance { c =>
    c.get[Int]("schemaVersion").flatMap {
      case 1 => decodeSchemaV1Manifest.apply(c)
      case 2 => decodeSchemaV2Manifest.apply(c)
      case _ => Xor.Left(DecodingFailure("Unsupported schemaVersion", c.history))
    }
  }

  implicit final val decodeSchemaV1Protected: Decoder[SchemaV1.Protected] =
    Decoder.forProduct3("formatLength", "formatTail", "time")(SchemaV1.Protected.apply)

  implicit final val decodeSchemaV2ImageRootFs: Decoder[SchemaV2.ImageRootFs] =
    Decoder.forProduct3("type", "diff_ids", "base_layer")(SchemaV2.ImageRootFs.apply)

  implicit final val decodeSchemaV2ImageHistory: Decoder[SchemaV2.ImageHistory] =
    Decoder.forProduct5("created", "author", "created_by", "comment", "empty_layer")(
      SchemaV2.ImageHistory.apply)

  implicit final val decodeSchemaV2ImageConfig: Decoder[SchemaV2.ImageConfig] =
    Decoder.forProduct3("rootfs", "history", "architecture")(SchemaV2.ImageConfig.apply)
}
