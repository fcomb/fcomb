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

import akka.actor.ActorSystem
import akka.http.scaladsl.util.FastFuture
import akka.stream.Materializer
import cats.data.{Validated, Xor}
import io.circe.generic.auto._
import io.circe.parser._
import io.circe.syntax._
import io.fcomb.persist.UsersRepo
import io.fcomb.services.EmailService
import io.fcomb.templates
import io.fcomb.utils.Config
import io.fcomb.validations.ValidationResultUnit
import org.slf4j.LoggerFactory
import pdi.jwt.{JwtAlgorithm, JwtCirce, JwtClaim}
import java.time.Instant
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

object ResetPassword {
  private val logger = LoggerFactory.getLogger(getClass)
  private val ttl    = Config.session.passwordResetTtl
  private val secret = Config.session.passwordResetSecret
  private val algo   = JwtAlgorithm.HS256

  // TODO: add email validation
  def reset(email: String)(
      implicit sys: ActorSystem,
      mat: Materializer
  ): Future[ValidationResultUnit] = {
    import mat.executionContext
    UsersRepo.findByEmail(email).fast.map {
      case Some(user) =>
        val expiration = Instant.now.plusSeconds(ttl)
        val token = JwtCirce.encode(
          JwtClaim(
            content = Payload(user.getId()).asJson.noSpaces,
            expiration = Some(expiration.getEpochSecond),
            issuedAt = Some(Instant.now.getEpochSecond)
          ),
          secret,
          algo
        )
        val template = templates.ResetPassword(
          s"title: token $token",
          s"date: $expiration",
          token
        )
        EmailService.sendTemplate(template, user.email, user.fullName)
        FastFuture.successful(Validated.Valid(()))
      case _ => UsersRepo.validationError("email", "not found")
    }
  }

  def set(token: String, password: String)(
      implicit ec: ExecutionContext,
      mat: Materializer
  ): Future[ValidationResultUnit] = {
    UsersRepo.validatePassword(password) match {
      case Validated.Valid(_) =>
        JwtCirce.decode(token, secret, Seq(algo)) match {
          case Success(jwtClaim) =>
            decode[Payload](jwtClaim.content.asJson.noSpaces) match {
              case Xor.Right(payload) =>
                UsersRepo.updatePassword(payload.userId, password).fast.map { isUpdated =>
                  if (isUpdated) Validated.Valid(())
                  else UsersRepo.validationError("id", "not found")
                }
              case Xor.Left(e) =>
                logger.debug("failed to decode token's payload: " + jwtClaim.content, e)
                FastFuture.successful(Validated.Valid(()))
            }
          case Failure(e) =>
            logger.debug("failed to decode token: " + token, e)
            FastFuture.successful(Validated.Valid(()))
        }
      case e @ Validated.Invalid(a) => FastFuture.successful(e)
    }
  }

  private case class Payload(userId: Int)
}
