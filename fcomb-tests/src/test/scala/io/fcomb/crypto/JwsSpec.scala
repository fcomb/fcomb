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
