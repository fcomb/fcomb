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

import cats.data.Validated
import cats.instances.either.catsStdInstancesForEither
import cats.syntax.cartesian._
import cats.syntax.show._
import io.circe._
import io.circe.jawn._
import io.circe.syntax._
import io.fcomb.crypto.Jws
import io.fcomb.services.EventService
import io.fcomb.json.models.docker.distribution.CompatibleFormats._
import io.fcomb.models.docker.distribution.ImageManifest.sha256Prefix
import io.fcomb.models.docker.distribution.SchemaV1.{Manifest => ManifestV1, _}
import io.fcomb.models.docker.distribution.SchemaV2.{Manifest => ManifestV2}
import io.fcomb.models.docker.distribution.{Image, ImageManifest, Reference}
import io.fcomb.models.errors.docker.distribution.DistributionError
import io.fcomb.persist.docker.distribution.ImageManifestsRepo
import io.fcomb.utils.StringUtils
import java.time.OffsetDateTime
import org.apache.commons.codec.digest.DigestUtils
import org.jose4j.base64url.Base64Url
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Either, Left, Right}

object SchemaV1 {
  def upsertAsImageManifest(
      image: Image,
      reference: Reference,
      manifest: ManifestV1,
      rawManifest: String,
      createdByUserId: Int
  )(implicit ec: ExecutionContext): Future[Either[DistributionError, String]] =
    verify(manifest, rawManifest) match {
      case Right((schemaV1JsonBlob, digest)) =>
        ImageManifestsRepo.upsertSchemaV1(image, manifest, schemaV1JsonBlob, digest).map {
          case Validated.Valid(imageManifest) =>
            EventService
              .pushRepoEvent(image, imageManifest.getId(), reference.value, createdByUserId)
            Right(digest)
          case Validated.Invalid(errs) =>
            Left(DistributionError.unknown(errs.map(_.message).mkString(";")))
        }
      case Left(e) => Future.successful(Left(DistributionError.unknown(e.message)))
    }

  /** Returns formatted manifest (without signatures) and its digest within [[cats.data.Either]] */
  def verify(manifest: ManifestV1,
             rawManifest: String): Either[DistributionError, (String, String)] =
    parse(rawManifest).map(_.asObject) match {
      case Right(Some(json)) =>
        val manifestJson = json.remove("signatures").asJson
        val original     = indentPrint(rawManifest, manifestJson)
        if (manifest.signatures.isEmpty)
          Left(DistributionError.unknown("signatures cannot be empty"))
        else {
          val rightAcc: Either[DistributionError, (String, String)] = Right(("", ""))
          manifest.signatures.foldLeft(rightAcc) {
            case (acc, signature) =>
              val `protected` = new String(base64Decode(signature.`protected`))
              acc *> (decode[Protected](`protected`) match {
                case Right(p) =>
                  val formatTail      = new String(base64Decode(p.formatTail))
                  val formatTailIndex = original.lastIndexOf(formatTail)
                  val formatted       = original.take(formatTailIndex + formatTail.length)
                  if (formatTailIndex == p.formatLength) {
                    val payload        = s"${signature.`protected`}.${base64Encode(formatted)}"
                    val signatureBytes = base64Decode(signature.signature)
                    val (alg, jwk)     = (signature.header.alg, signature.header.jwk)
                    if (Jws.verify(alg, jwk, payload, signatureBytes))
                      Right((original, DigestUtils.sha256Hex(formatted)))
                    else Left(DistributionError.manifestUnverified)
                  } else
                    Left(
                      DistributionError.manifestInvalid(
                        "formatted length does not match with fortmatLength"))
                case Left(e) => Left(DistributionError.unknown(e.show))
              })
          }
        }
      case Right(None) => Left(DistributionError.manifestInvalid())
      case Left(e)     => Left(DistributionError.unknown(e.show))
    }

  def convertFromSchemaV2(image: Image,
                          manifest: ManifestV2,
                          imageConfigJson: Json): Either[String, String] =
    (for {
      imgConfig <- decodeSchemaV2ImageConfig.decodeJson(imageConfigJson)
      config    <- decodeSchemaV1Config.decodeJson(imageConfigJson)
    } yield (imgConfig, config)) match {
      case Right((imgConfig, config)) =>
        if (imgConfig.history.isEmpty) Left("Image config history is empty")
        else if (imgConfig.rootFs.diffIds.isEmpty) Left("Image config root fs is empty")
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
            case Some(obj) => Right(prettyPrint(obj.remove("signatures").asJson))
            case None      => Left("manifestV1 not a JSON object")
          }
        }
      case Left(e) => Left(e.show)
    }

  def addSignature(manifestV1: String): Either[String, String] =
    parseManifestV1(manifestV1).map(signManifestV1)

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

  def addTagAndSignature(manifestV1: String, tag: String): Either[String, String] =
    parseManifestV1(manifestV1).map { manifest =>
      val manifestWithTag = manifest.add("tag", Json.fromString(tag))
      signManifestV1(manifestWithTag)
    }

  private def parseManifestV1(manifestV1: String): Either[String, JsonObject] =
    parse(manifestV1).map(_.asObject) match {
      case Right(opt) =>
        opt match {
          case Some(manifest) => Right(manifest)
          case _              => Left("Manifest V1 not a JSON object")
        }
      case Left(e) => Left(e.show)
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
      time = OffsetDateTime.now()
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
