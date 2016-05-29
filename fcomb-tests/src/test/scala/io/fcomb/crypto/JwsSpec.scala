package io.fcomb.crypto

import org.scalatest.{Matchers, WordSpec}
import org.jose4j.jwk.PublicJsonWebKey

class JwsSpec extends WordSpec with Matchers {
  "JWS module" should {
    "make key id from public key" in {
      val pk = PublicJsonWebKey.Factory.newPublicJwk("""
      {
        "crv": "P-256",
        "kty": "EC",
        "x": "2n_STWmA8MkbGND3JDpK8Uol72UgbHLMU98xMeuIgYI",
        "y": "yFHo6U2ml_UUV1udjby1yT0cm2OGTSI5QVwpv0Et36M"
      }
      """.trim)
      val keyId = "TLT3:64VE:CEIR:WI2I:7SSB:SYUG:NBSU:TQZ4:YSCD:TM3Y:J3JH:J57V"
      Jws.keyId(pk.getPublicKey) shouldEqual keyId
    }
  }
}
