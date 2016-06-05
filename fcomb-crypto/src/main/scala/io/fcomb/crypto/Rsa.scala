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

import java.security.{Key, KeyFactory, PublicKey, PrivateKey}
import java.security.spec.{PKCS8EncodedKeySpec, X509EncodedKeySpec}
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
