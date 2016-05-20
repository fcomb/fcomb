package io.fcomb.crypto

import java.security.{ Key, KeyFactory, PublicKey, PrivateKey }
import java.security.spec.{ PKCS8EncodedKeySpec, X509EncodedKeySpec }
import javax.crypto.Cipher

object Rsa {
  def loadPrivateKey(key: Array[Byte]) = {
    val spec = new PKCS8EncodedKeySpec(key)
    KeyFactory.getInstance("RSA").generatePrivate(spec)
  }

  def loadPublicKey(key: Array[Byte]) = {
    val spec = new X509EncodedKeySpec(key)
    KeyFactory.getInstance("RSA").generatePublic(spec)
  }

  private def getCipher(mode: Int, key: Key) = {
    val cipher = Cipher.getInstance("RSA")
    cipher.init(mode, key)
    cipher
  }

  def encrypt(bytes: Array[Byte], key: PublicKey): Array[Byte] =
    getCipher(Cipher.ENCRYPT_MODE, key).doFinal(bytes)

  def decrypt(bytes: Array[Byte], key: PrivateKey): Array[Byte] =
    getCipher(Cipher.DECRYPT_MODE, key).doFinal(bytes)
}
