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

package io.fcomb.crypto

import com.typesafe.scalalogging.LazyLogging
import org.apache.commons.codec.binary.Base32
import org.apache.commons.codec.digest.DigestUtils
import org.jose4j.jca.ProviderContext
import org.jose4j.jwk.{EllipticCurveJsonWebKey, EcJwkGenerator, JsonWebKey}
import org.jose4j.jws.EcdsaUsingShaAlgorithm
import org.jose4j.keys.EllipticCurves
import scala.collection.JavaConverters._
import scala.collection.immutable
import java.security.PublicKey

object Jws extends LazyLogging {
  def verify(algorithm: String,
             params: immutable.Map[String, String],
             payload: String,
             signatureBytes: Array[Byte]): Boolean = {
    try {
      val jwk = JsonWebKey.Factory
        .newJwk(params.toMap[String, Object].asJava)
        .asInstanceOf[EllipticCurveJsonWebKey]
      val alg = algorithm match {
        case "ES256" => new EcdsaUsingShaAlgorithm.EcdsaP256UsingSha256()
        case "ES384" => new EcdsaUsingShaAlgorithm.EcdsaP384UsingSha384()
        case "ES512" => new EcdsaUsingShaAlgorithm.EcdsaP521UsingSha512()
        case _       => throw new IllegalArgumentException(s"Unknown algorithm: $algorithm")
      }
      val pk = jwk.getPublicKey
      Option(jwk.getKeyId).contains(keyId(pk)) &&
      alg.verifySignature(signatureBytes, pk, payload.getBytes("utf-8"), ctx)
    } catch {
      case e: Throwable =>
        logger.error(e.getMessage, e)
        false
    }
  }

  private lazy val defaultEcJwk = {
    val jwk = EcJwkGenerator.generateJwk(EllipticCurves.P256)
    jwk.setKeyId(keyId(jwk.getPublicKey))
    jwk
  }
  lazy val defaultEcJwkParams: immutable.Map[String, String] = defaultEcJwk
    .toParams(JsonWebKey.OutputControlLevel.PUBLIC_ONLY)
    .asScala
    .map { case (k, v) => (k, v.asInstanceOf[String]) }
    .toMap

  def signWithDefaultJwk(bytes: Array[Byte]): (Array[Byte], String) = {
    val alg       = new EcdsaUsingShaAlgorithm.EcdsaP256UsingSha256()
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

  private val ctx = new ProviderContext()
}
