package io.fcomb

import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.security.Security

package object crypto {
  Security.addProvider(new BouncyCastleProvider())
}
