package io.fcomb.crypto

import java.security.cert.X509Certificate
import java.io.StringWriter
import java.security.{KeyStore, PrivateKey, SecureRandom, Signature}
import java.util.{Calendar, Date, Vector => JavaVector}
import java.util.concurrent.ThreadLocalRandom
import org.bouncycastle.openssl.jcajce._
import java.security.cert.{Certificate => JavaCertificate}
import sun.security.tools.keytool.CertAndKeyGen
import sun.security.x509._
import sun.security.util.ObjectIdentifier

object Certificate {
  initializeCrypto()

  val keyAlgName = "SHA256WithRSA"
  val defaultExpireAfterDays = 365
  val defaultKeySize = 2048

  def generateCertAndKey(keySize: Int) = {
    val keypair = new CertAndKeyGen("RSA", keyAlgName, null)
    keypair.generate(keySize)
    keypair
  }

  def rootAuthorityExtensions(keypair: CertAndKeyGen) = {
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
    exts
  }

  def generateRootAuthority(
    organizationalUnit: String,
    organization: String,
    city: String,
    state: String,
    country: String,
    password: String,
    expireAfterDays: Int = defaultExpireAfterDays,
    commonName: String = "ROOT CA",
    keySize: Int = defaultKeySize
  ) = {
    val x500Name = new X500Name(
      commonName,
      organizationalUnit,
      organization,
      city,
      state,
      country
    )
    val keypair = generateCertAndKey(keySize)
    val privateKey = keypair.getPrivateKey()
    val cert = keypair.getSelfCertificate(
      x500Name,
      new Date(),
      expireAfterDays.toLong * 24 * 60 * 60,
      rootAuthorityExtensions(keypair)
    )
    (cert, privateKey)
  }

  val clientExtensions = {
    val exts = new CertificateExtensions()
    val v = new JavaVector[ObjectIdentifier]()
    v.add(new ObjectIdentifier("1.3.6.1.5.5.7.3.2")) // clientAuth
    exts.set(ExtendedKeyUsageExtension.NAME, new ExtendedKeyUsageExtension(v))
    exts
  }

  def generateClient(
    signerCert: JavaCertificate,
    signerPrivateKey: PrivateKey,
    commonName: String = "client",
    expireAfterDays: Int = defaultExpireAfterDays,
    keySize: Int = defaultKeySize
  ) = {
    val signerCertImpl = new X509CertImpl(signerCert.getEncoded())
    val signerCertInfo = signerCertImpl.get(s"${X509CertImpl.NAME}.${X509CertImpl.INFO}")
      .asInstanceOf[X509CertInfo]
    val issuer = signerCertInfo.get(s"${X509CertInfo.SUBJECT}.${X509CertInfo.DN_NAME}")
      .asInstanceOf[X500Name]
    val expireDate = Calendar.getInstance()
    expireDate.add(Calendar.DATE, expireAfterDays)
    val interval = new CertificateValidity(new Date(), expireDate.getTime())
    val signature = Signature.getInstance(keyAlgName)
    signature.initSign(signerPrivateKey)
    val info = new X509CertInfo()
    info.set(X509CertInfo.VALIDITY, interval)
    info.set(
      X509CertInfo.SERIAL_NUMBER,
      new CertificateSerialNumber(ThreadLocalRandom.current().nextInt() & 0x7fffffff)
    )
    info.set(
      X509CertInfo.VERSION,
      new CertificateVersion(CertificateVersion.V3)
    )
    info.set(
      X509CertInfo.ALGORITHM_ID,
      new CertificateAlgorithmId(AlgorithmId.get(keyAlgName))
    )
    info.set(X509CertInfo.ISSUER, issuer)

    val keypair = generateCertAndKey(keySize)
    val req = keypair.getCertRequest(new X500Name(s"CN=$commonName"))
    info.set(X509CertInfo.KEY, new CertificateX509Key(req.getSubjectPublicKeyInfo()))
    info.set(X509CertInfo.SUBJECT, req.getSubjectName())

    val cert = new X509CertImpl(info)
    cert.sign(signerPrivateKey, keyAlgName)

    (cert, keypair.getPrivateKey())
  }

  def toPem(cert: X509Certificate) = {
    val writer = new StringWriter()
    val pw = new JcaPEMWriter(writer)
    pw.writeObject(cert)
    pw.flush()
    writer.toString()
  }

  def toPem(key: PrivateKey, password: Option[String]) = {
    val writer = new StringWriter()
    val pw = new JcaPEMWriter(writer)
    val random = SecureRandom.getInstance("SHA1PRNG")
    password match {
      case Some(pass) =>
        val encryptor = new JcePEMEncryptorBuilder("AES-256-CBC")
          .setSecureRandom(random)
          .build(pass.toCharArray())
        pw.writeObject(key, encryptor)
      case _ => pw.writeObject(key)
    }
    pw.flush()
    writer.toString()
  }
}
