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

import cats.data.Xor
import io.circe.{Encoder, Decoder}
import io.circe.syntax._
import io.circe.generic.semiauto._
import pdi.jwt.{JwtAlgorithm, JwtCirce, JwtClaim}
import java.time.Instant
import scala.util.{Failure, Success}

object Jwt {
  private val algo = JwtAlgorithm.HS256

  private sealed trait Payload
  private[this] final case class UserPayload(userId: Int) extends Payload

  private[this] implicit lazy val encodeUserPayload: Encoder[UserPayload] = deriveEncoder
  private[this] implicit lazy val decodeUserPayload: Decoder[UserPayload] = deriveDecoder

  def encode(userId: Int, secret: String, issuedAt: Instant, ttl: Long) = {
    val claim = JwtClaim(
      content = UserPayload(userId).asJson.noSpaces,
      expiration = Some(issuedAt.plusSeconds(ttl).getEpochSecond),
      issuedAt = Some(issuedAt.getEpochSecond)
    )
    JwtCirce.encode(claim, secret, algo)
  }

  def decode(token: String, secret: String): Xor[String, Int] = {
    JwtCirce.decode(token, secret, Seq(algo)) match {
      case Success(jwtClaim) =>
        decodeUserPayload
          .decodeJson(jwtClaim.content.asJson)
          .map(_.userId)
          .leftMap(_ => "failed to decode token's payload")
      case Failure(e) => Xor.Left("failed to decode token")
    }
  }
}
