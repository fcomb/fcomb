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

import java.security.cert.X509Certificate
import java.io.StringWriter
import java.security.{PrivateKey, PublicKey, Signature}
import java.util.{Calendar, Date, Vector => JavaVector}
import java.util.concurrent.ThreadLocalRandom
import org.bouncycastle.openssl.jcajce.JcaPEMWriter
import java.security.cert.{Certificate => JavaCertificate}
import sun.security.tools.keytool.CertAndKeyGen
import sun.security.x509._
import sun.security.pkcs10.PKCS10
import sun.security.util.ObjectIdentifier

object Certificate {
  initializeCrypto()

  // TODO: move these options into config {
  val keyAlgorithmName       = "SHA256WithRSA"
  val defaultExpireAfterDays = 365
  val defaultKeySize         = 2048
  // }

  def generateRootAuthority(
      organizationalUnit: String,
      organization: String,
      city: String,
      state: String,
      country: String,
      expireAfterDays: Int = defaultExpireAfterDays,
      commonName: String = "ROOT CA",
      keySize: Int = defaultKeySize
  ) = {
    val issuer = new X500Name(
        commonName,
        organizationalUnit,
        organization,
        city,
        state,
        country
    )
    val keypair    = generateCertAndKey(keySize)
    val privateKey = keypair.getPrivateKey()

    val info = x509CertInfo(issuer, expireAfterDays)
    info.set(X509CertInfo.KEY, new CertificateX509Key(keypair.getPublicKey()))
    info.set(X509CertInfo.SUBJECT, issuer)
    val serialNumber = info
      .get(X509CertInfo.SERIAL_NUMBER)
      .asInstanceOf[CertificateSerialNumber]
      .get(CertificateSerialNumber.NUMBER)
    val ext = rootAuthorityExtensions(
        issuer,
        keypair.getPublicKey(),
        serialNumber
    )
    info.set(X509CertInfo.EXTENSIONS, ext)

    val cert = new X509CertImpl(info)
    cert.sign(privateKey, keyAlgorithmName)

    (cert.asInstanceOf[X509Certificate], privateKey)
  }

  def generateClient(
      signerCert: JavaCertificate,
      signerPrivateKey: PrivateKey,
      commonName: String = "client",
      expireAfterDays: Int = defaultExpireAfterDays,
      keySize: Int = defaultKeySize
  ) =
    generateSignedCertificate(
        signerCert,
        signerPrivateKey,
        new X500Name(s"CN=$commonName"),
        clientExtensions,
        expireAfterDays,
        keySize
    )

  def generateServer(
      signerCert: JavaCertificate,
      signerPrivateKey: PrivateKey,
      hostname: String,
      expireAfterDays: Int = defaultExpireAfterDays,
      keySize: Int = defaultKeySize
  ) =
    generateSignedCertificate(
        signerCert,
        signerPrivateKey,
        new X500Name(s"CN=$hostname"),
        serverExtensions(hostname),
        expireAfterDays,
        keySize
    )

  def signCertificationRequest(
      signerCert: JavaCertificate,
      signerPrivateKey: PrivateKey,
      request: PKCS10,
      name: X500Name,
      extOpt: Option[CertificateExtensions] = None,
      expireAfterDays: Int = defaultExpireAfterDays
  ) = {
    val signerCertImpl = new X509CertImpl(signerCert.getEncoded())
    val signerCertInfo =
      signerCertImpl.get(s"${X509CertImpl.NAME}.${X509CertImpl.INFO}").asInstanceOf[X509CertInfo]
    val issuer =
      signerCertInfo.get(s"${X509CertInfo.SUBJECT}.${X509CertInfo.DN_NAME}").asInstanceOf[X500Name]
    val signature = Signature.getInstance(keyAlgorithmName)
    signature.initSign(signerPrivateKey)
    val info = x509CertInfo(issuer, expireAfterDays)
    info.set(
        X509CertInfo.KEY,
        new CertificateX509Key(request.getSubjectPublicKeyInfo())
    )
    info.set(X509CertInfo.SUBJECT, request.getSubjectName())
    extOpt.foreach(ext => info.set(X509CertInfo.EXTENSIONS, ext))
    val cert = new X509CertImpl(info)
    cert.sign(signerPrivateKey, keyAlgorithmName)
    cert.asInstanceOf[X509Certificate]
  }

  def toPem(cert: X509Certificate) =
    getAsPem(cert)

  def toPem(key: PrivateKey) =
    getAsPem(key)

  private def getAsPem(key: Any) = {
    val writer = new StringWriter()
    val pw     = new JcaPEMWriter(writer)
    pw.writeObject(key)
    pw.flush()
    writer.toString()
  }

  private def generateCertAndKey(keySize: Int) = {
    val keypair = new CertAndKeyGen("RSA", keyAlgorithmName, null)
    keypair.generate(keySize)
    keypair
  }

  private def x509CertInfo(issuer: X500Name, expireAfterDays: Int) = {
    val expireDate = Calendar.getInstance()
    expireDate.add(Calendar.DATE, expireAfterDays)
    val interval = new CertificateValidity(new Date(), expireDate.getTime())
    val info     = new X509CertInfo()
    info.set(X509CertInfo.VALIDITY, interval)
    val serialNumber = ThreadLocalRandom.current().nextInt() & 0x7fffffff
    info.set(
        X509CertInfo.SERIAL_NUMBER,
        new CertificateSerialNumber(serialNumber)
    )
    info.set(
        X509CertInfo.VERSION,
        new CertificateVersion(CertificateVersion.V3)
    )
    val alg = AlgorithmId.get(keyAlgorithmName)
    info.set(X509CertInfo.ALGORITHM_ID, new CertificateAlgorithmId(alg))
    info.set(X509CertInfo.ISSUER, issuer)
    info
  }

  private def generateSignedCertificate(
      signerCert: JavaCertificate,
      signerPrivateKey: PrivateKey,
      name: X500Name,
      ext: CertificateExtensions,
      expireAfterDays: Int,
      keySize: Int
  ) = {
    val signerCertImpl = new X509CertImpl(signerCert.getEncoded())
    val signerCertInfo =
      signerCertImpl.get(s"${X509CertImpl.NAME}.${X509CertImpl.INFO}").asInstanceOf[X509CertInfo]
    val issuer =
      signerCertInfo.get(s"${X509CertInfo.SUBJECT}.${X509CertInfo.DN_NAME}").asInstanceOf[X500Name]
    val signature = Signature.getInstance(keyAlgorithmName)
    signature.initSign(signerPrivateKey)
    val info    = x509CertInfo(issuer, expireAfterDays)
    val keypair = generateCertAndKey(keySize)
    val req     = keypair.getCertRequest(name)
    info.set(
        X509CertInfo.KEY,
        new CertificateX509Key(req.getSubjectPublicKeyInfo())
    )
    info.set(X509CertInfo.SUBJECT, req.getSubjectName())
    info.set(X509CertInfo.EXTENSIONS, ext)
    val cert = new X509CertImpl(info)
    cert.sign(signerPrivateKey, keyAlgorithmName)
    (cert.asInstanceOf[X509Certificate], keypair.getPrivateKey())
  }

  private def rootAuthorityExtensions(
      issuer: X500Name,
      publicKey: PublicKey,
      serialNumber: SerialNumber
  ) = {
    val keyIdentifier = new KeyIdentifier(publicKey)
    val exts          = new CertificateExtensions()
    exts.set(
        BasicConstraintsExtension.NAME,
        new BasicConstraintsExtension(true, true, 0)
    )
    val kue = new KeyUsageExtension()
    kue.set(KeyUsageExtension.CRL_SIGN, true)
    kue.set(KeyUsageExtension.KEY_CERTSIGN, true)
    exts.set(KeyUsageExtension.NAME, kue)
    exts.set(
        SubjectKeyIdentifierExtension.NAME,
        new SubjectKeyIdentifierExtension(keyIdentifier.getIdentifier())
    )
    val generalNames = new GeneralNames().add(new GeneralName(issuer))
    exts.set(
        AuthorityKeyIdentifierExtension.NAME,
        new AuthorityKeyIdentifierExtension(
            keyIdentifier,
            generalNames,
            serialNumber
        )
    )
    exts
  }

  private def serverExtensions(hostname: String) = {
    val exts = new CertificateExtensions()
    val dns  = new GeneralNames().add(new GeneralName(new DNSName(hostname)))
    exts.set(
        SubjectAlternativeNameExtension.NAME,
        new SubjectAlternativeNameExtension(dns)
    )
    val v = new JavaVector[ObjectIdentifier]()
    v.add(new ObjectIdentifier("1.3.6.1.5.5.7.3.1")) // serverAuth
    exts.set(ExtendedKeyUsageExtension.NAME, new ExtendedKeyUsageExtension(v))
    exts
  }

  private val clientExtensions = {
    val exts = new CertificateExtensions()
    val v    = new JavaVector[ObjectIdentifier]()
    v.add(new ObjectIdentifier("1.3.6.1.5.5.7.3.2")) // clientAuth
    exts.set(ExtendedKeyUsageExtension.NAME, new ExtendedKeyUsageExtension(v))
    exts
  }
}
