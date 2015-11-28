package io.fcomb.crypto

import java.io._
import java.security.{KeyStore, PrivateKey, SecureRandom}
import java.util.Date
import org.bouncycastle.openssl.jcajce._
import sun.security.tools.keytool.CertAndKeyGen
import sun.security.x509._

object Ca {
  initializeCrypto()

  def generate() = {
    val keyStore = KeyStore.getInstance(KeyStore.getDefaultType)
    keyStore.load(null, null)

    val keypair = new CertAndKeyGen("RSA", "SHA256withRSA", null)
    val x500Name = new X500Name(
      "ROOT CA",
      "organizationalUnit",
      "organization",
      "city",
      "state",
      "country"
    )
    keypair.generate(2048)
    val privKey = keypair.getPrivateKey()

    val exts = new CertificateExtensions()
    exts.set(BasicConstraintsExtension.NAME, new BasicConstraintsExtension(true, true, 0))
    val kue = new KeyUsageExtension()
    kue.set(KeyUsageExtension.CRL_SIGN, true)
    kue.set(KeyUsageExtension.KEY_CERTSIGN, true)
    exts.set(KeyUsageExtension.NAME, kue)
    exts.set(SubjectKeyIdentifierExtension.NAME, new SubjectKeyIdentifierExtension(new KeyIdentifier(keypair.getPublicKey()).getIdentifier()))
    // TODO: AuthorityKeyIdentifierExtension
    val cert = keypair.getSelfCertificate(x500Name, new Date(), 3650L * 24 * 60 * 60, exts)
    val pubwriter = new FileWriter(new File("/tmp/ca.pem"))
    val pubpw = new JcaPEMWriter(pubwriter)
    pubpw.writeObject(cert)
    pubpw.flush()

    val keyPass = "password".toCharArray()
    val writer = new FileWriter(new File("/tmp/ca-key.pem"))
    val pw = new JcaPEMWriter(writer)
    val random = SecureRandom.getInstance("SHA1PRNG")
    val encryptor = new JcePEMEncryptorBuilder("AES-256-CBC")
      .setSecureRandom(random)
      .build(keyPass)
    pw.writeObject(privKey, encryptor)
    pw.flush()

    println("done!")
  }
}
