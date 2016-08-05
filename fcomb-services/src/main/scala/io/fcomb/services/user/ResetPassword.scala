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
import akka.http.scaladsl.util.FastFuture, FastFuture._
import akka.stream.Materializer
import cats.data.{Validated, Xor}
import io.fcomb.crypto.Jwt
import io.fcomb.persist.UsersRepo
import io.fcomb.services.EmailService
import io.fcomb.templates
import io.fcomb.utils.Config
import io.fcomb.validations.ValidationResultUnit
import java.time.Instant
import scala.concurrent.{ExecutionContext, Future}

object ResetPassword {
  // TODO: add email validation
  def reset(email: String)(implicit sys: ActorSystem,
                           mat: Materializer): Future[ValidationResultUnit] = {
    import mat.executionContext
    UsersRepo.findByEmail(email).fast.map {
      case Some(user) =>
        val timeNow = Instant.now()
        val jwt     = Jwt.encode(user.getId(), Config.jwt.secret, timeNow, Config.jwt.resetPasswordTtl)
        val template = templates.ResetPassword(
          s"title: token $jwt",
          s"date: $timeNow",
          jwt
        )
        EmailService.sendTemplate(template, user.email, user.fullName)
        Validated.Valid(())
      case _ => UsersRepo.validationError("email", "not found")
    }
  }

  def set(token: String, password: String)(
      implicit ec: ExecutionContext): Future[ValidationResultUnit] = {
    Jwt.decode(token, Config.jwt.secret) match {
      case Xor.Right(userId) => UsersRepo.updatePassword(userId, password)
      case Xor.Left(msg)     => UsersRepo.validationErrorAsFuture("token", msg)
    }
  }
}
