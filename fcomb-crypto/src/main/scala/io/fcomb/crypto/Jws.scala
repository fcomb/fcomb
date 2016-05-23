package io.fcomb.crypto

import org.jose4j.jca.ProviderContext
import org.jose4j.jwk.{EllipticCurveJsonWebKey, JsonWebKey}
import org.jose4j.jws.EcdsaUsingShaAlgorithm
import org.slf4j.LoggerFactory
import scala.collection.JavaConverters._
import scala.collection.immutable

object Jws {
  def verifySignature(
    algorithm:      String,
    jwk:            immutable.Map[String, String],
    payload:        String,
    signatureBytes: Array[Byte]
  ): Boolean = {
    try {
      val key = JsonWebKey.Factory
        .newJwk(jwk.toMap[String, Object].asJava)
        .asInstanceOf[EllipticCurveJsonWebKey]
      val alg = algorithm match {
        case "ES256" ⇒ new EcdsaUsingShaAlgorithm.EcdsaP256UsingSha256()
        case "ES384" ⇒ new EcdsaUsingShaAlgorithm.EcdsaP384UsingSha384()
        case "ES512" ⇒ new EcdsaUsingShaAlgorithm.EcdsaP521UsingSha512()
        case _ ⇒
          throw new IllegalArgumentException(s"Unknown algorithm: $algorithm")
      }
      alg.verifySignature(
        signatureBytes, key.getPublicKey(), payload.getBytes("utf-8"), ctx
      )
    }
    catch {
      case e: Throwable ⇒
        logger.error(e.getMessage, e)
        false
    }
  }

  private lazy val logger = LoggerFactory.getLogger(getClass)

  private val ctx = new ProviderContext()
}
