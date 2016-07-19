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

package io.fcomb.docker.distribution.manifest

import akka.http.scaladsl.util.FastFuture, FastFuture._
import cats.data.{Validated, Xor}
import cats.syntax.cartesian._
import cats.syntax.show._
import io.circe._, io.circe.parser._, io.circe.syntax._
import io.fcomb.crypto.Jws
import io.fcomb.json.models.docker.distribution.CompatibleFormats._
import io.fcomb.models.docker.distribution.SchemaV1.{Manifest => ManifestV1, _}
import io.fcomb.models.docker.distribution.SchemaV2.{ImageConfig, Manifest => ManifestV2}
import io.fcomb.models.docker.distribution.{Reference, ImageManifest => ImageManifest, Image => Image},
ImageManifest.sha256Prefix
import io.fcomb.models.errors.docker.distribution.DistributionError, DistributionError._
import io.fcomb.persist.docker.distribution.ImageManifestsRepo
import io.fcomb.utils.StringUtils
import java.time.ZonedDateTime
import org.apache.commons.codec.digest.DigestUtils
import org.jose4j.base64url.Base64Url
import scala.concurrent.{ExecutionContext, Future}

object SchemaV1 {
  def upsertAsImageManifest(
      image: Image,
      reference: Reference,
      manifest: ManifestV1,
      rawManifest: String
  )(implicit ec: ExecutionContext): Future[Xor[DistributionError, String]] = {
    verify(manifest, rawManifest) match {
      case Xor.Right((schemaV1JsonBlob, digest)) =>
        ImageManifestsRepo.upsertSchemaV1(image, manifest, schemaV1JsonBlob, digest).fast.map {
          case Validated.Valid(_)   => Xor.right(digest)
          case Validated.Invalid(e) => Xor.left(Unknown(e.map(_.message).mkString(";")))
        }
      case Xor.Left(e) => FastFuture.successful(Xor.left(Unknown(e.message)))
    }
  }

  def verify(manifest: ManifestV1, rawManifest: String): Xor[DistributionError, (String, String)] = {
    parse(rawManifest).map(_.asObject) match {
      case Xor.Right(Some(json)) =>
        val manifestJson = json.remove("signatures").asJson
        val original     = indentPrint(rawManifest, manifestJson)
        if (manifest.signatures.isEmpty) Xor.left(Unknown("signatures cannot be empty"))
        else {
          val rightAcc = Xor.right[DistributionError, (String, String)](("", ""))
          manifest.signatures.foldLeft(rightAcc) {
            case (acc, signature) =>
              val `protected` = new String(base64Decode(signature.`protected`))
              acc *> (decode[Protected](`protected`) match {
                    case Xor.Right(p) =>
                      val formatTail      = new String(base64Decode(p.formatTail))
                      val formatTailIndex = original.lastIndexOf(formatTail)
                      val formatted       = original.take(formatTailIndex + formatTail.length)
                      if (formatTailIndex == p.formatLength) {
                        val payload        = s"${signature.`protected`}.${base64Encode(formatted)}"
                        val signatureBytes = base64Decode(signature.signature)
                        val (alg, jwk)     = (signature.header.alg, signature.header.jwk)
                        if (Jws.verify(alg, jwk, payload, signatureBytes))
                          Xor.right((rawManifest, DigestUtils.sha256Hex(formatted)))
                        else Xor.left(ManifestUnverified())
                      } else
                        Xor.left(
                          ManifestInvalid("formatted length does not match with fortmatLength"))
                    case Xor.Left(e) => Xor.left(Unknown(e.show))
                  })
          }
        }
      case Xor.Right(None) => Xor.left(ManifestInvalid())
      case Xor.Left(e)     => Xor.left(Unknown(e.show))
    }
  }

  def convertFromSchemaV2(
      image: Image,
      manifest: ManifestV2,
      imageConfig: String
  ): Xor[String, String] = {
    (for {
      imgConfig <- decode[ImageConfig](imageConfig)
      config    <- decode[Config](imageConfig)(decodeSchemaV1Config)
    } yield (imgConfig, config)) match {
      case Xor.Right((imgConfig, config)) =>
        if (imgConfig.history.isEmpty) Xor.left("Image config history is empty")
        else if (imgConfig.rootFs.diffIds.isEmpty) Xor.left("Image config root fs is empty")
        else {
          val baseLayerId = imgConfig.rootFs.baseLayer.map(DigestUtils.sha384Hex(_).take(32))
          val (lastParentId, remainLayers, history, fsLayers) = imgConfig.history.init
            .foldLeft(("", manifest.layers, List.empty[Layer], List.empty[FsLayer])) {
              case ((parentId, layers, historyList, fsLayersList), img) =>
                val (blobSum, layersTail) =
                  if (img.isEmptyLayer) (ImageManifest.emptyTarSha256DigestFull, layers)
                  else {
                    val head = layers.headOption.map(_.getDigest).getOrElse("")
                    (head, layers.tail)
                  }
                val v1Id      = DigestUtils.sha256Hex(s"$blobSum $parentId")
                val createdBy = img.createdBy.map(List(_)).getOrElse(Nil)
                val throwAway = if (img.isEmptyLayer) Some(true) else None
                val historyLayer = Layer(
                  id = v1Id,
                  parent = StringUtils.trim(Some(parentId)),
                  comment = img.comment,
                  created = Some(img.created),
                  containerConfig = Some(LayerContainerConfig(createdBy)),
                  author = img.author,
                  throwAway = throwAway
                )
                val fsLayer = FsLayer(s"$sha256Prefix$blobSum")
                val currentId =
                  if (parentId.isEmpty) baseLayerId.getOrElse(v1Id)
                  else v1Id
                (currentId, layersTail, historyLayer :: historyList, fsLayer :: fsLayersList)
            }

          val (configHistory, configFsLayer) = {
            val isEmptyLayer = imgConfig.history.last.isEmptyLayer
            val blobSum =
              if (isEmptyLayer) ImageManifest.emptyTarSha256DigestFull
              else remainLayers.headOption.map(_.getDigest).getOrElse("")
            val v1Id = DigestUtils.sha256Hex(s"$blobSum $lastParentId $imgConfig")
            val parent =
              if (lastParentId.isEmpty) config.parent
              else Some(lastParentId)
            val throwAway = if (isEmptyLayer) Some(true) else None
            val historyLayer = config.copy(
              id = Some(v1Id),
              parent = parent,
              throwAway = throwAway
            )
            val fsLayer = FsLayer(s"$sha256Prefix$blobSum")
            (historyLayer, fsLayer)
          }

          val manifestV1 = ManifestV1(
            name = image.slug,
            tag = "",
            fsLayers = configFsLayer :: fsLayers,
            architecture = imgConfig.architecture,
            history = configHistory :: history,
            signatures = Nil
          )
          manifestV1.asJson.asObject match {
            case Some(obj) => Xor.right(prettyPrint(obj.remove("signatures").asJson))
            case None      => Xor.left("manifestV1 not an JSON object")
          }
        }
      case Xor.Left(e) => Xor.left(e.show)
    }
  }

  def addSignature(manifestV1: String): String = {
    signManifestV1(parseManifestV1(manifestV1))
  }

  private def signManifestV1(manifest: JsonObject): String = {
    val formatted        = prettyPrint(manifest.asJson)
    val `protected`      = protectedHeader(formatted)
    val payload          = s"${`protected`}.${base64Encode(formatted)}"
    val (signature, alg) = Jws.signWithDefaultJwk(payload.getBytes("utf-8"))
    val signatures = List(
      Signature(
        header = SignatureHeader(Jws.defaultEcJwkParams, alg),
        signature = base64Encode(signature),
        `protected` = `protected`
      ))
    val manifestWithSignature = manifest.add("signatures", signatures.asJson)
    prettyPrint(manifestWithSignature.asJson)
  }

  def addTagAndSignature(manifestV1: String, tag: String): String = {
    val manifest        = parseManifestV1(manifestV1)
    val manifestWithTag = manifest.add("tag", Json.fromString(tag))
    signManifestV1(manifestWithTag)
  }

  // TODO: add Xor
  private def parseManifestV1(manifestV1: String): JsonObject = {
    parse(manifestV1).map(_.asObject) match {
      case Xor.Right(opt) =>
        opt match {
          case Some(manifest) => manifest
          case _              => throw new IllegalArgumentException("Manifest V1 not a JSON object")
        }
      case Xor.Left(e) => throw new IllegalArgumentException(e.show)
    }
  }

  private val spaceCharSet = Set[Char]('\t', '\n', 0x0B, '\f', '\r', ' ', 0x85, 0xA0)

  private def isSpace(c: Char): Boolean =
    spaceCharSet.contains(c)

  private def protectedHeader(formatted: String): String = {
    val cbIndex      = formatted.lastIndexOf('}') - 1
    val formatLength = formatted.lastIndexWhere(!isSpace(_), cbIndex) + 1
    val formatTail   = formatted.drop(formatLength)
    val p = Protected(
      formatLength = formatLength,
      formatTail = base64Encode(formatTail),
      time = ZonedDateTime.now().withFixedOffsetZone()
    )
    base64Encode(p.asJson.noSpaces)
  }

  def prettyPrint(json: Json): String =
    printer("   ").pretty(json)

  def prettyPrint(m: ManifestV1): String =
    prettyPrint(m.asJson)

  def indentPrint(original: String, json: Json) = {
    val indent = original.dropWhile(_ != ' ').takeWhile(_ == ' ')
    printer(indent).pretty(json)
  }

  private val base64url = new Base64Url()

  private def base64Encode(bytes: Array[Byte]): String =
    base64url.base64UrlEncode(bytes)

  private def base64Encode(s: String): String =
    base64url.base64UrlEncode(s.getBytes("utf-8"))

  private def base64Decode(s: String): Array[Byte] =
    base64url.base64UrlDecode(s)

  private def printer(indent: String) = Printer(
    preserveOrder = true,
    dropNullKeys = false,
    indent = indent,
    lbraceRight = "\n",
    rbraceLeft = "\n",
    lbracketRight = "\n",
    rbracketLeft = "\n",
    lrbracketsEmpty = "\n",
    arrayCommaRight = "\n",
    objectCommaRight = "\n",
    colonLeft = "",
    colonRight = " "
  )
}
