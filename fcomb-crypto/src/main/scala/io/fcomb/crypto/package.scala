package io.fcomb

import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.security.Security

package object crypto {
  private val initializeCryptoResult = {
    Security.addProvider(new BouncyCastleProvider())

    val field = Class.forName("javax.crypto.JceSecurity")
      .getDeclaredField("isRestricted")
    field.setAccessible(true)
    field.set(null, false)
  }

  def initializeCrypto() = initializeCryptoResult
}
