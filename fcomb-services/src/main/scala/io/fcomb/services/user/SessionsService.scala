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
import io.fcomb.crypto.Jwt
import io.fcomb.models.errors.Errors
import io.fcomb.models.{Session, User}
import io.fcomb.persist.UsersRepo
import io.fcomb.rpc.SessionCreateRequest
import io.fcomb.utils.Config
import java.time.Instant
import scala.concurrent.{ExecutionContext, Future}

object SessionsService {
  private val invalidEmailOrPassword = Xor.Left(
    Errors(
      Seq(
        Errors.validation("email", "invalid"),
        Errors.validation("password", "invalid")
      )))

  def create(req: SessionCreateRequest)(
      implicit ec: ExecutionContext): Future[Xor[Errors, Session]] =
    UsersRepo.findByEmail(req.email).fast.map {
      case Some(user) if user.isValidPassword(req.password) =>
        val timeNow = Instant.now()
        val jwt     = Jwt.encode(user, Config.jwt.secret, timeNow, Config.jwt.sessionTtl)
        Xor.Right(Session(jwt))
      case _ => invalidEmailOrPassword
    }

  def find(token: String)(implicit ec: ExecutionContext): Future[Option[User]] =
    Jwt.decode(token, Config.jwt.secret) match {
      case Xor.Right(payload) => UsersRepo.findById(payload.id)
      case _                  => FastFuture.successful(None)
    }
}
