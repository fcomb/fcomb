package io.fcomb.crypto

import org.apache.commons.codec.binary.Base32
import org.apache.commons.codec.digest.DigestUtils
import org.jose4j.jca.ProviderContext
import org.jose4j.jwk.{EllipticCurveJsonWebKey, EcJwkGenerator, JsonWebKey}
import org.jose4j.jws.EcdsaUsingShaAlgorithm
import org.jose4j.keys.EllipticCurves
import org.slf4j.LoggerFactory
import scala.collection.JavaConverters._
import scala.collection.immutable
import java.security.PublicKey

object Jws {
  def verify(
    algorithm:      String,
    params:         immutable.Map[String, String],
    payload:        String,
    signatureBytes: Array[Byte]
  ): Boolean = {
    try {
      val jwk = JsonWebKey.Factory
        .newJwk(params.toMap[String, Object].asJava)
        .asInstanceOf[EllipticCurveJsonWebKey]
      val alg = algorithm match {
        case "ES256" ⇒ new EcdsaUsingShaAlgorithm.EcdsaP256UsingSha256()
        case "ES384" ⇒ new EcdsaUsingShaAlgorithm.EcdsaP384UsingSha384()
        case "ES512" ⇒ new EcdsaUsingShaAlgorithm.EcdsaP521UsingSha512()
        case _       ⇒ throw new IllegalArgumentException(s"Unknown algorithm: $algorithm")
      }
      val pk = jwk.getPublicKey
      Option(jwk.getKeyId).contains(keyId(pk)) &&
        alg.verifySignature(signatureBytes, pk, payload.getBytes("utf-8"), ctx)
    }
    catch {
      case e: Throwable ⇒
        logger.error(e.getMessage, e)
        false
    }
  }

  private lazy val defaultEcJwk = {
    val jwk = EcJwkGenerator.generateJwk(EllipticCurves.P256)
    jwk.setKeyId(keyId(jwk.getPublicKey))
    jwk
  }
  lazy val defaultEcJwkParams: immutable.Map[String, String] =
    defaultEcJwk.toParams(JsonWebKey.OutputControlLevel.PUBLIC_ONLY)
      .asScala
      .map { case (k, v) ⇒ (k, v.asInstanceOf[String]) }
      .toMap

  def signWithDefaultJwk(bytes: Array[Byte]): (Array[Byte], String) = {
    val alg = new EcdsaUsingShaAlgorithm.EcdsaP256UsingSha256()
    val signature = alg.sign(defaultEcJwk.getPrivateKey, bytes, ctx)
    (signature, "ES256")
  }

  def keyId(pk: PublicKey): String = {
    val bytes = DigestUtils.sha256(pk.getEncoded).take(30)
    val b32 = {
      val s = new Base32().encodeToString(bytes)
      if (s.endsWith("=")) s.take(s.indexOf('='))
      else s
    }
    b32.grouped(4).mkString(":")
  }

  private lazy val logger = LoggerFactory.getLogger(getClass)

  private val ctx = new ProviderContext()
}
