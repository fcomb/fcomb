package io.fcomb.crypto

import java.io.StringWriter
import java.security.{KeyStore, PrivateKey, SecureRandom}
import java.util.Date
import org.bouncycastle.openssl.jcajce._
import sun.security.tools.keytool.CertAndKeyGen
import sun.security.x509._

object Certificate {
  initializeCrypto()

  case class Certificate(certificate: String, key: String)

  def generateRootAuthority(
    organizationalUnit: String,
    organization: String,
    city: String,
    state: String,
    country: String,
    password: String,
    expireAfterDays: Long = 3650L,
    commonName: String = "ROOT CA",
    keySize: Int = 2048
  ) = {
    val keyStore = KeyStore.getInstance(KeyStore.getDefaultType)
    keyStore.load(null, null)

    val keypair = new CertAndKeyGen("RSA", "SHA256withRSA", null)
    val x500Name = new X500Name(
      commonName,
      organizationalUnit,
      organization,
      city,
      state,
      country
    )
    keypair.generate(keySize)
    val privateKey = keypair.getPrivateKey()
    val exts = new CertificateExtensions()
    exts.set(BasicConstraintsExtension.NAME, new BasicConstraintsExtension(true, true, 0))
    val kue = new KeyUsageExtension()
    kue.set(KeyUsageExtension.CRL_SIGN, true)
    kue.set(KeyUsageExtension.KEY_CERTSIGN, true)
    exts.set(KeyUsageExtension.NAME, kue)
    exts.set(
      SubjectKeyIdentifierExtension.NAME,
      new SubjectKeyIdentifierExtension(
        new KeyIdentifier(keypair.getPublicKey()).getIdentifier()
      )
    )
    // TODO: AuthorityKeyIdentifierExtension
    val cert = keypair.getSelfCertificate(
      x500Name,
      new Date(),
      expireAfterDays * 24 * 60 * 60,
      exts
    )

    val certWriter = new StringWriter()
    val certPW = new JcaPEMWriter(certWriter)
    certPW.writeObject(cert)
    certPW.flush()

    val keyWriter = new StringWriter()
    val keyPW = new JcaPEMWriter(keyWriter)
    val random = SecureRandom.getInstance("SHA1PRNG")
    val encryptor = new JcePEMEncryptorBuilder("AES-256-CBC")
      .setSecureRandom(random)
      .build(password.toCharArray())
    keyPW.writeObject(privateKey, encryptor)
    keyPW.flush()

    Certificate(certWriter.toString(), keyWriter.toString)
  }
}
