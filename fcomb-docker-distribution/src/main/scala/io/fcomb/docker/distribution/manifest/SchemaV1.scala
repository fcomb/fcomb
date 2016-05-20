package io.fcomb.docker.distribution.manifest

import cats.data.Xor
import cats.syntax.cartesian._
import io.circe._, io.circe.parser._, io.circe.syntax._
import io.fcomb.crypto.Jws
import io.fcomb.json.docker.distribution.Formats.decodeSchemaV1Protected
import io.fcomb.models.docker.distribution.SchemaV1.{ Manifest ⇒ ManifestV1, Protected, Layer, FsLayer, LayerContainerConfig }
import io.fcomb.models.docker.distribution.SchemaV2.{ Manifest ⇒ ManifestV2 }
import io.fcomb.models.docker.distribution.{ ImageManifest ⇒ MImageManifest, Image ⇒ MImage }, MImageManifest.sha256Prefix
import io.fcomb.models.errors.docker.distribution.DistributionError, DistributionError._
import io.fcomb.utils.StringUtils
import org.apache.commons.codec.digest.DigestUtils
import org.jose4j.base64url.Base64Url

object SchemaV1 {
  def verify(
    manifest:    ManifestV1,
    rawManifest: String
  ): Xor[DistributionError, String] = {
    parse(rawManifest).map(_.asObject) match {
      case Xor.Right(Some(json)) ⇒
        val indent = rawManifest.dropWhile(_ != ' ').takeWhile(_ == ' ')
        val original = printer(indent).pretty(json.remove("signatures").asJson)
        if (manifest.signatures.isEmpty) Xor.left(Unknown("signatures cannot be empty"))
        else
          manifest.signatures.foldLeft(
            Xor.right[DistributionError, String]("")
          ) {
              case (acc, signature) ⇒
                val `protected` =
                  new String(base64url.base64UrlDecode(signature.`protected`))
                acc *> (decode[Protected](`protected`) match {
                  case Xor.Right(p) ⇒
                    val formatTailIndex = original.lastIndexOf(p.formatTail)
                    val formatted =
                      original.take(formatTailIndex + p.formatTail.length)
                    if (formatTailIndex == p.formatLength) {
                      val payload = s"${signature.`protected`}.${
                        base64url.base64UrlEncode(formatted.getBytes("utf-8"))
                      }"
                      val signatureBytes = base64url.base64UrlDecode(signature.signature)
                      val (alg, jwk) = (signature.header.alg, signature.header.jwk)
                      if (Jws.verifySignature(alg, jwk, payload, signatureBytes))
                        Xor.right(DigestUtils.sha256Hex(formatted))
                      else Xor.left(ManifestUnverified())
                    }
                    else Xor.left(ManifestInvalid("formatted length does not match with fortmatLength"))
                  case Xor.Left(e) ⇒ Xor.left(Unknown(e.getMessage))
                })
            }
      case Xor.Right(None) ⇒ Xor.left(ManifestInvalid())
      case Xor.Left(e)     ⇒ Xor.left(Unknown(e.getMessage))
    }
  }

  import io.fcomb.json.docker.distribution.Formats._
  import io.fcomb.models.docker.distribution.SchemaV2.ImageConfig
  import io.fcomb.models.docker.distribution.SchemaV1.Config
  def asJsonBlob(
    image:           MImage,
    manifest:        ManifestV2,
    manifestJson:    String,
    imageConfigJson: String
  ): Xor[DistributionError, ManifestV1] = {
    (for {
      imageConfig ← decode[ImageConfig](imageConfigJson)
      config ← decode[Config](imageConfigJson)(decodeSchemaV1Config)
    } yield (imageConfig, config)) match {
      case Xor.Right((imageConfig, config)) ⇒
        assert(imageConfig.history.nonEmpty)
        assert(imageConfig.rootFs.diffIds.nonEmpty)
        val baseLayerId =
          imageConfig.rootFs.baseLayer.map(DigestUtils.sha384Hex(_).take(32))
        val (lastParentId, remainLayers, history, fsLayers) =
          imageConfig.history.init.foldLeft(
            ("", manifest.layers, List.empty[Layer], List.empty[FsLayer])
          ) {
              case ((parentId, layers, historyList, fsLayersList), img) ⇒
                val (blobSum, layersTail) =
                  if (img.isEmptyLayer)
                    (MImageManifest.emptyTarSha256DigestFull, layers)
                  else {
                    val head = layers.headOption
                      .map(_.digest.drop(sha256Prefix.length))
                      .getOrElse("")
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
                (
                  currentId,
                  layersTail,
                  historyLayer :: historyList,
                  fsLayer :: fsLayersList
                )
            }

        val (configHistory, configFsLayer) = {
          val isEmptyLayer = imageConfig.history.last.isEmptyLayer
          val blobSum =
            if (isEmptyLayer) MImageManifest.emptyTarSha256DigestFull
            else
              remainLayers.headOption
                .map(_.digest.drop(sha256Prefix.length))
                .getOrElse("")
          val v1Id =
            DigestUtils.sha256Hex(s"$blobSum $lastParentId $imageConfigJson")
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

        Xor.right(
          ManifestV1(
            name = image.name,
            tag = "",
            fsLayers = configFsLayer :: fsLayers,
            architecture = imageConfig.architecture,
            history = configHistory :: history,
            signatures = Nil
          )
        )
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
