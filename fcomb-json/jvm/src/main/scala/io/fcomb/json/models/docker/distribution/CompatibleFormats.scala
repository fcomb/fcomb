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

import enumeratum.Circe
import io.circe.generic.semiauto._
import io.circe.java8.time._
import io.circe.jawn._
import io.circe.{
  Decoder,
  DecodingFailure,
  Encoder,
  HCursor,
  Json,
  KeyDecoder,
  ParsingFailure,
  Printer
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

  final implicit val encodeSchemaV1ContainerConfig: Encoder[SchemaV1.ContainerConfig] =
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

  final implicit val encodeSchemaV1LayerContainerConfig: Encoder[SchemaV1.LayerContainerConfig] =
    Encoder.forProduct1("Cmd")(SchemaV1.LayerContainerConfig.unapply(_).get)

  final val encodeSchemaV1Layer: Encoder[SchemaV1.Layer] = Encoder
    .forProduct7("id", "parent", "comment", "created", "container_config", "author", "throwaway")(
      SchemaV1.Layer.unapply(_).get)

  final implicit val encodeSchemaV1FsLayer: Encoder[SchemaV1.FsLayer] =
    Encoder.forProduct1("blobSum")(SchemaV1.FsLayer.unapply(_).get)

  final implicit val encodeSchemaV1Compatibility = new Encoder[SchemaV1.Compatibility] {
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

  final implicit val encodeSignatureHeader: Encoder[SchemaV1.SignatureHeader] = deriveEncoder

  final implicit val encodeSchemaV1Signature: Encoder[SchemaV1.Signature] =
    Encoder.forProduct3("header", "signature", "protected")(SchemaV1.Signature.unapply(_).get)

  final implicit val encodeSchemaV1Manifest: Encoder[SchemaV1.Manifest] =
    Encoder.forProduct7("name",
                        "tag",
                        "fsLayers",
                        "architecture",
                        "history",
                        "signatures",
                        "schemaVersion")(SchemaV1.Manifest.unapply(_).get)

  final implicit val encodeSchemaV1Protected: Encoder[SchemaV1.Protected] =
    Encoder.forProduct3("formatLength", "formatTail", "time")(SchemaV1.Protected.unapply(_).get)

  final implicit val decodeDistributionErrorCode = Circe.decoder(DistributionErrorCode)

  final implicit def decodeCanBuildFromWithNull[A, C[_]](
      implicit d: Decoder[A],
      cbf: CanBuildFrom[Nothing, A, C[A]]
  ): Decoder[C[A]] = new Decoder[C[A]] {
    final def apply(c: HCursor): Decoder.Result[C[A]] =
      if (c.focus.isEmpty || c.value.isNull) Right(cbf.apply.result)
      else Decoder.decodeCanBuildFrom(d, cbf).apply(c)
  }

  final implicit def decodeMapLikeWithNull[M[K, +V] <: Map[K, V], K, V](
      implicit dk: KeyDecoder[K],
      dv: Decoder[V],
      cbf: CanBuildFrom[Nothing, (K, V), M[K, V]]
  ): Decoder[M[K, V]] = new Decoder[M[K, V]] {
    final def apply(c: HCursor): Decoder.Result[M[K, V]] =
      if (c.focus.isEmpty || c.value.isNull) Right(cbf.apply.result)
      else Decoder.decodeMapLike(dk, dv, cbf).apply(c)
  }

  final implicit val decodeSchemaV1LayerContainerConfig: Decoder[SchemaV1.LayerContainerConfig] =
    Decoder.forProduct1("Cmd")(SchemaV1.LayerContainerConfig.apply)

  final val decodeSchemaV1Layer = Decoder
    .forProduct7("id", "parent", "comment", "created", "container_config", "author", "throwaway")(
      SchemaV1.Layer.apply)

  final implicit val decodeSchemaV1LayerFromV1Compatibility: Decoder[SchemaV1.Layer] =
    Decoder.instance { c =>
      c.get[String]("v1Compatibility").flatMap { cs =>
        parse(cs).map(_.hcursor) match {
          case Right(hc)                    => decodeSchemaV1Layer(hc)
          case Left(ParsingFailure(msg, _)) => Left(DecodingFailure(msg, Nil))
        }
      }
    }

  final implicit val decodeSchemaV1ContainerConfig: Decoder[SchemaV1.ContainerConfig] =
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

  final implicit val decodeSchemaV1ConfigFromV1Compatibility: Decoder[SchemaV1.Config] =
    Decoder.instance { c =>
      c.get[String]("v1Compatibility").flatMap { cs =>
        parse(cs).map(_.hcursor) match {
          case Right(hc)                    => decodeSchemaV1Config(hc)
          case Left(ParsingFailure(msg, _)) => Left(DecodingFailure(msg, Nil))
        }
      }
    }

  final implicit val decodeSchemaV1CompatibilityList: Decoder[List[SchemaV1.Compatibility]] =
    Decoder.instance { c =>
      c.value.asArray match {
        case Some(configJson +: xsJson) =>
          for {
            config <- configJson.as[SchemaV1.Config]
            acc: Either[DecodingFailure, List[SchemaV1.Layer]] = Right(Nil)
            xs <- xsJson.foldRight(acc) { (item, lacc) =>
              for {
                acc   <- lacc
                layer <- item.as[SchemaV1.Layer]
              } yield layer :: acc
            }
          } yield config :: xs
        case _ => Left(DecodingFailure("history", c.history))
      }
    }

  final implicit val decodeSchemaV1SignatureHeader: Decoder[SchemaV1.SignatureHeader] =
    deriveDecoder

  final implicit val decodeSchemaV1Signature: Decoder[SchemaV1.Signature] = deriveDecoder

  final implicit val decodeSchemaV1FsLayer: Decoder[SchemaV1.FsLayer] = deriveDecoder

  final implicit val decodeSchemaV1Manifest: Decoder[SchemaV1.Manifest] = deriveDecoder

  final implicit val decodeSchemaV2Descriptor: Decoder[SchemaV2.Descriptor] = deriveDecoder

  final implicit val decodeSchemaV2Manifest: Decoder[SchemaV2.Manifest] = deriveDecoder

  final implicit val decodeSchemaManifest: Decoder[SchemaManifest] = Decoder.instance { c =>
    c.get[Int]("schemaVersion").flatMap {
      case 1 => decodeSchemaV1Manifest.apply(c)
      case 2 => decodeSchemaV2Manifest.apply(c)
      case _ => Left(DecodingFailure("Unsupported schemaVersion", c.history))
    }
  }

  final implicit val decodeSchemaV1Protected: Decoder[SchemaV1.Protected] =
    Decoder.forProduct3("formatLength", "formatTail", "time")(SchemaV1.Protected.apply)

  final implicit val decodeSchemaV2ImageRootFs: Decoder[SchemaV2.ImageRootFs] =
    Decoder.forProduct3("type", "diff_ids", "base_layer")(SchemaV2.ImageRootFs.apply)

  final implicit val decodeSchemaV2ImageHistory: Decoder[SchemaV2.ImageHistory] =
    Decoder.forProduct5("created", "author", "created_by", "comment", "empty_layer")(
      SchemaV2.ImageHistory.apply)

  final implicit val decodeSchemaV2ImageConfig: Decoder[SchemaV2.ImageConfig] =
    Decoder.forProduct3("rootfs", "history", "architecture")(SchemaV2.ImageConfig.apply)
}
