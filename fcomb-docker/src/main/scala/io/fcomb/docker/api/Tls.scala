package io.fcomb.docker.api

import javax.net.ssl.{KeyManagerFactory, SSLContext, SSLEngine, TrustManagerFactory, X509TrustManager}
import java.security.{KeyFactory, KeyPair, KeyStore, SecureRandom, Security}
import java.security.cert.{Certificate, CertificateFactory, X509Certificate}
import java.security.spec.PKCS8EncodedKeySpec
import java.nio.file.{Files, Paths}
import java.io.{BufferedReader, FileInputStream, FileReader}

// TODO: move into fcomb-crypto project
object Ssl {
  def context(keyPath: String, certPath: String, caPath: String) = {
    def certificate(path: String): Certificate = {
      val certStm = new FileInputStream(path)
      try CertificateFactory.getInstance("X.509").generateCertificate(certStm)
      finally certStm.close()
    }

    val keyManagers = {
      val keyBytes = Files.readAllBytes(Paths.get(keyPath))
      val spec = new PKCS8EncodedKeySpec(keyBytes)
      val kf = KeyFactory.getInstance("RSA")
      val key = kf.generatePrivate(spec)
      val keyStore = KeyStore.getInstance(KeyStore.getDefaultType)
      keyStore.load(null, null)
      keyStore.setKeyEntry(
        "key",
        key,
        "".toCharArray,
        Array(certificate(certPath))
      )
      val kmf = KeyManagerFactory.getInstance("SunX509")
      kmf.init(keyStore, "".toCharArray)
      kmf.getKeyManagers()
    }

    val trustManager = {
      val trustStore = KeyStore.getInstance(KeyStore.getDefaultType)
      trustStore.load(null, null)
      trustStore.setCertificateEntry("cacert", certificate(caPath))
      val fact = TrustManagerFactory.getInstance("SunX509")
      fact.init(trustStore)
      fact.getTrustManagers
    }

    val ctx = SSLContext.getInstance("TLS")
    ctx.init(keyManagers, trustManager, new SecureRandom)
    ctx
  }

  // import akka.http.scaladsl.HttpsContext
  // val httpsContext = HttpsContext(TLS(
  //   "/tmp/coreos/client.der",
  //   "/tmp/coreos/client.pem",
  //   "/tmp/coreos/ca.pem"
  // ).certify())
}
