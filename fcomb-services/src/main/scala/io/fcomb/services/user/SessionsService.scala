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

package io.fcomb.services.user

import akka.http.scaladsl.util.FastFuture, FastFuture._
import cats.data.Xor
import io.circe.generic.auto._
import io.circe.parser._
import io.circe.syntax._
import io.fcomb.models.errors.{FailureResponse, ValidationException}
import io.fcomb.models.{Session, User}
import io.fcomb.persist.UsersRepo
import io.fcomb.rpc.SessionCreateRequest
import io.fcomb.utils.Config
import org.slf4j.LoggerFactory
import pdi.jwt.{JwtAlgorithm, JwtCirce, JwtClaim}
import java.time.Instant
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

private[this] final case class Payload(userId: Int)

object SessionsService {
  private val logger = LoggerFactory.getLogger(getClass)
  private val ttl    = Config.session.ttl
  private val secret = Config.session.secret
  private val algo   = JwtAlgorithm.HS256

  private val invalidEmailOrPassword = Xor.Left(
    FailureResponse.fromExceptions(
      Seq(
        ValidationException("email", "invalid"),
        ValidationException("password", "invalid")
      )))

  def create(req: SessionCreateRequest)(
      implicit ec: ExecutionContext): Future[Xor[FailureResponse, Session]] = {
    UsersRepo.findByEmail(req.email).fast.map {
      case Some(user) if user.isValidPassword(req.password) =>
        val claim = JwtClaim(
          content = Payload(user.getId()).asJson.noSpaces,
          expiration = Some(Instant.now.plusSeconds(ttl).getEpochSecond),
          issuedAt = Some(Instant.now.getEpochSecond)
        )
        val jwt = JwtCirce.encode(claim, secret, algo)
        Xor.Right(Session(jwt))
      case _ => invalidEmailOrPassword
    }
  }

  def find(token: String)(implicit ec: ExecutionContext): Future[Option[User]] = {
    JwtCirce.decode(token, secret, Seq(algo)) match {
      case Success(jwtClaim) =>
        decode[Payload](jwtClaim.content.asJson.noSpaces) match {
          case Xor.Right(payload) =>
            UsersRepo.findById(payload.userId)
          case Xor.Left(e) =>
            logger.debug("failed to decode token's payload: " + jwtClaim.content, e)
            FastFuture.successful(None)
        }
      case Failure(e) =>
        logger.debug("failed to decode token: " + token, e)
        FastFuture.successful(None)
    }
  }
}
