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

import cats.syntax.either._
import io.circe.jawn.{decode => jsonDecode}
import io.circe.syntax._
import io.fcomb.models.{SessionPayload, User}
import io.fcomb.json.models.Formats.{decodeSessionPayloadUser, encodeSessionPayloadUser}
import java.time.Instant
import pdi.jwt.{JwtAlgorithm, JwtCirce, JwtClaim}
import scala.util.{Either, Failure, Success}

object Jwt {
  private val algo = JwtAlgorithm.HS256

  def encode(payload: SessionPayload.User, secret: String, issuedAt: Instant, ttl: Long): String = {
    val claim = JwtClaim(
      content = payload.asJson.noSpaces,
      expiration = Some(issuedAt.plusSeconds(ttl).getEpochSecond),
      issuedAt = Some(issuedAt.getEpochSecond)
    )
    JwtCirce.encode(claim, secret, algo)
  }

  def encode(user: User, secret: String, issuedAt: Instant, ttl: Long): String = {
    val payload = SessionPayload.User(user.getId(), user.username)
    encode(payload, secret, issuedAt, ttl)
  }

  def decode(token: String, secret: String): Either[String, SessionPayload.User] =
    JwtCirce.decode(token, secret, Seq(algo)) match {
      case Success(jwtClaim) =>
        jsonDecode[SessionPayload.User](jwtClaim.content).leftMap(_ =>
          "failed to decode token's payload")
      case Failure(e) => Left("failed to decode token")
    }
}
