package io.fcomb.docker.distribution.manifest

import cats.data.Xor
import io.circe._, io.circe.parser._, io.circe.syntax._
import io.fcomb.crypto.Jws
import io.fcomb.models.docker.distribution.ManifestV1
import org.jose4j.base64url.Base64Url

object Schema1 {
  def verify(manifest: ManifestV1, rawManifest: String): Xor[String, Unit] = {
    parse(rawManifest).map(_.asObject) match {
      case Xor.Right(Some(json)) ⇒
        val indent = rawManifest.dropWhile(_ != ' ').takeWhile(_ == ' ')
        val res = golangPrinter(indent).pretty(json.remove("signatures").asJson)
        if (manifest.signatures.exists(_.length > 1))
          Xor.Left("x509 chain signatures is not supported yet")
        else manifest.signatures.flatMap(_.headOption) match {
          case Some(signature) ⇒
            val payload = s"${signature.`protected`}.${base64url.base64UrlEncode(res.getBytes("utf-8"))}"
            val signatureBytes = base64url.base64UrlDecode(signature.signature)
            val (alg, jwk) = (signature.header.alg, signature.header.jwk)
            if (Jws.verifySignature(alg, jwk, payload, signatureBytes)) Xor.Right(())
            else Xor.Left("Invalid signature")
          case None ⇒ Xor.Right(())
        }
      case Xor.Right(None) ⇒ Xor.Left("Json body is not a manifest schema1 object")
      case Xor.Left(e)     ⇒ Xor.Left(e.getMessage)
    }
  }

  private val base64url = new Base64Url()

  private def golangPrinter(indent: String) = Printer(
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
