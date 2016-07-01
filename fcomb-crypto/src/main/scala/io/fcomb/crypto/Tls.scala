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

import java.io.ByteArrayInputStream
import java.nio.file.{Files, Path}
import java.security.{KeyFactory, KeyStore, SecureRandom}
import java.security.cert.{Certificate => JavaCertificate, CertificateFactory}
import java.security.spec.PKCS8EncodedKeySpec
import javax.net.ssl.{KeyManagerFactory, SSLContext, TrustManagerFactory}

object Tls {
  def context(
      key: Array[Byte],
      cert: Array[Byte],
      ca: Option[Array[Byte]]
  ): SSLContext = {
    def certificate(bytes: Array[Byte]): JavaCertificate = {
      val is = new ByteArrayInputStream(bytes)
      try CertificateFactory.getInstance("X.509").generateCertificate(is)
      finally is.close()
    }

    val keyManagers = {
      val spec       = new PKCS8EncodedKeySpec(key)
      val kf         = KeyFactory.getInstance("RSA")
      val privateKey = kf.generatePrivate(spec)
      val keyStore   = KeyStore.getInstance(KeyStore.getDefaultType)
      keyStore.load(null, null)
      keyStore.setKeyEntry(
        "key",
        privateKey,
        "".toCharArray,
        Array(certificate(cert))
      )
      val kmf = KeyManagerFactory.getInstance("SunX509")
      kmf.init(keyStore, "".toCharArray)
      kmf.getKeyManagers()
    }

    val trustManager = ca match {
      case Some(ca) =>
        val trustStore = KeyStore.getInstance(KeyStore.getDefaultType)
        trustStore.load(null, null)
        trustStore.setCertificateEntry("cacert", certificate(ca))
        val fact = TrustManagerFactory.getInstance("SunX509")
        fact.init(trustStore)
        fact.getTrustManagers
      case None => null
    }

    val ctx = SSLContext.getInstance("TLSv1.2")
    ctx.init(keyManagers, trustManager, new SecureRandom)
    ctx
  }

  def context(key: String, cert: String, ca: Option[String]): SSLContext =
    context(key.getBytes, cert.getBytes, ca.map(_.getBytes))

  def context(key: Path, cert: Path, ca: Option[Path]): SSLContext =
    context(readFile(key), readFile(cert), ca.map(ca => readFile(ca)))

  private def readFile(p: Path) = Files.readAllBytes(p)
}
