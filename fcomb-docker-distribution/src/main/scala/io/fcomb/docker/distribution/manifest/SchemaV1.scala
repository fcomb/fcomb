package io.fcomb.docker.distribution.manifest

import akka.http.scaladsl.util.FastFuture
import cats.data.Xor
import cats.syntax.cartesian._
import io.circe._, io.circe.parser._, io.circe.syntax._
import io.fcomb.crypto.Jws
import io.fcomb.json.docker.distribution.Formats._
import io.fcomb.json.docker.distribution.Formats.decodeSchemaV1Protected
import io.fcomb.models.docker.distribution.SchemaV1.{ Manifest ⇒ ManifestV1, Protected, Layer, FsLayer, LayerContainerConfig, Config }
import io.fcomb.models.docker.distribution.SchemaV2.{ ImageConfig, Manifest ⇒ ManifestV2 }
import io.fcomb.models.docker.distribution.{ ImageManifest ⇒ MImageManifest, Image ⇒ MImage }, MImageManifest.sha256Prefix
import io.fcomb.models.errors.docker.distribution.DistributionError, DistributionError._
import io.fcomb.persist.docker.distribution.{ ImageManifest ⇒ PImageManifest }
import io.fcomb.utils.StringUtils
import org.apache.commons.codec.digest.DigestUtils
import org.jose4j.base64url.Base64Url
import scala.concurrent.Future

object SchemaV1 {
  def upsertAsImageManifest(
    image:       MImage,
    reference:   String,
    manifest:    ManifestV1,
    rawManifest: String
  ): Future[Xor[DistributionError, String]] = {
    verify(manifest, rawManifest) match {
      case Xor.Right((manifestJson, sha256Digest)) ⇒
        FastFuture.successful(Xor.right(sha256Digest))
      case Xor.Left(e) ⇒ ???
    }
  }

  def verify(manifest: ManifestV1, rawManifest: String): Xor[DistributionError, (Json, String)] = {
    parse(rawManifest).map(_.asObject) match {
      case Xor.Right(Some(json)) ⇒
        val indent = rawManifest.dropWhile(_ != ' ').takeWhile(_ == ' ')
        val manifestJson = json.remove("signatures").asJson
        val original = printer(indent).pretty(manifestJson)
        if (manifest.signatures.isEmpty) Xor.left(Unknown("signatures cannot be empty"))
        else {
          val z = Xor.right[DistributionError, (Json, String)]((Json.Null, ""))
          manifest.signatures.foldLeft(z) {
            case (acc, signature) ⇒
              val `protected` = new String(base64url.base64UrlDecode(signature.`protected`))
              acc *> (decode[Protected](`protected`) match {
                case Xor.Right(p) ⇒
                  val formatTailIndex = original.lastIndexOf(p.formatTail)
                  val formatted = original.take(formatTailIndex + p.formatTail.length)
                  if (formatTailIndex == p.formatLength) {
                    val payload = s"${signature.`protected`}.${
                      base64url.base64UrlEncode(formatted.getBytes("utf-8"))
                    }"
                    val signatureBytes = base64url.base64UrlDecode(signature.signature)
                    val (alg, jwk) = (signature.header.alg, signature.header.jwk)
                    if (Jws.verifySignature(alg, jwk, payload, signatureBytes))
                      Xor.right((manifestJson, DigestUtils.sha256Hex(formatted)))
                    else Xor.left(ManifestUnverified())
                  }
                  else Xor.left(ManifestInvalid("formatted length does not match with fortmatLength"))
                case Xor.Left(e) ⇒ Xor.left(Unknown(e.getMessage))
              })
          }
        }
      case Xor.Right(None) ⇒ Xor.left(ManifestInvalid())
      case Xor.Left(e)     ⇒ Xor.left(Unknown(e.getMessage))
    }
  }

  def convertFromSchemaV2(
    image:           MImage,
    manifest:        ManifestV2,
    manifestJson:    String,
    imageConfigJson: String
  ): Xor[String, ManifestV1] = {
    (for {
      imageConfig ← decode[ImageConfig](imageConfigJson)
      config ← decode[Config](imageConfigJson)(decodeSchemaV1Config)
    } yield (imageConfig, config)) match {
      case Xor.Right((imageConfig, config)) ⇒
        if (imageConfig.history.isEmpty) Xor.left("Image config history is empty")
        else if (imageConfig.rootFs.diffIds.isEmpty) Xor.left("Image config root fs is empty")
        else {
          val baseLayerId = imageConfig.rootFs.baseLayer.map(DigestUtils.sha384Hex(_).take(32))
          val (lastParentId, remainLayers, history, fsLayers) =
            imageConfig.history.init.foldLeft(("", manifest.layers, List.empty[Layer], List.empty[FsLayer])) {
              case ((parentId, layers, historyList, fsLayersList), img) ⇒
                val (blobSum, layersTail) =
                  if (img.isEmptyLayer) (MImageManifest.emptyTarSha256DigestFull, layers)
                  else {
                    val head = layers.headOption.map(_.parseDigest).getOrElse("")
                    (head, layers.tail)
                  }
                val v1Id = DigestUtils.sha256Hex(s"$blobSum $parentId")
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
            val isEmptyLayer = imageConfig.history.last.isEmptyLayer
            val blobSum =
              if (isEmptyLayer) MImageManifest.emptyTarSha256DigestFull
              else remainLayers.headOption.map(_.parseDigest).getOrElse("")
            val v1Id = DigestUtils.sha256Hex(s"$blobSum $lastParentId $imageConfigJson")
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

          Xor.right(ManifestV1(
            name = image.name,
            tag = "",
            fsLayers = configFsLayer :: fsLayers,
            architecture = imageConfig.architecture,
            history = configHistory :: history,
            signatures = Nil
          ))
        }
      case Xor.Left(e) ⇒ Xor.left(e.getMessage)
    }
  }

  private val base64url = new Base64Url()

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