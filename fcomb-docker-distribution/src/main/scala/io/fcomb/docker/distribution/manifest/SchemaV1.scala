package io.fcomb.docker.distribution.manifest

import cats.data.Xor
import io.circe._, io.circe.parser._, io.circe.syntax._
import io.fcomb.crypto.Jws
import io.fcomb.models.docker.distribution.SchemaV1.{Manifest, Protected}
import io.fcomb.models.errors.docker.distribution.DistributionError
import io.fcomb.json.docker.distribution.Formats.decodeSchemaV1Protected
import org.jose4j.base64url.Base64Url

object SchemaV1 {
  def verify(manifest: Manifest, rawManifest: String): Xor[DistributionError, Unit] = {
    parse(rawManifest).map(_.asObject) match {
      case Xor.Right(Some(json)) ⇒
        val indent = rawManifest.dropWhile(_ != ' ').takeWhile(_ == ' ')
        val original = printer(indent).pretty(json.remove("signatures").asJson)
        if (manifest.signatures.exists(_.length > 1))
          Xor.left(DistributionError.Unknown("x509 chain signatures is not supported yet"))
        else manifest.signatures.flatMap(_.headOption) match {
          case Some(signature) ⇒
            val `protected` = new String(base64url.base64UrlDecode(signature.`protected`))
            decode[Protected](`protected`) match {
              case Xor.Right(p) ⇒
                val formatTailIndex = original.lastIndexOf(p.formatTail)
                val formatted = original.take(formatTailIndex + p.formatTail.length)
                if (formatTailIndex == p.formatLength) {
                  val payload = s"${signature.`protected`}.${base64url.base64UrlEncode(formatted.getBytes("utf-8"))}"
                  val signatureBytes = base64url.base64UrlDecode(signature.signature)
                  val (alg, jwk) = (signature.header.alg, signature.header.jwk)
                  if (Jws.verifySignature(alg, jwk, payload, signatureBytes)) Xor.right(())
                  else Xor.left(DistributionError.ManifestUnverified())
                }
                else Xor.left(DistributionError.ManifestInvalid("formatted length does not match with fortmatLength"))
              case Xor.Left(e) ⇒ Xor.left(DistributionError.Unknown(e.getMessage))
            }
          case None ⇒ Xor.right(())
        }
      case Xor.Right(None) ⇒ Xor.left(DistributionError.ManifestInvalid())
      case Xor.Left(e)     ⇒ Xor.left(DistributionError.Unknown(e.getMessage))
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
