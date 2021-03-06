/*
 * Copyright 2017 fcomb. <https://fcomb.io>
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

import io.fcomb.config.Settings
import io.fcomb.crypto.Jwt
import io.fcomb.models.errors.Errors
import io.fcomb.models.{Session, User}
import io.fcomb.persist.UsersRepo
import io.fcomb.PostgresProfile.api.Database
import io.fcomb.rpc.SessionCreateRequest
import java.time.Instant
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Either, Left, Right}

object SessionsService {
  def create(req: SessionCreateRequest)(implicit ec: ExecutionContext,
                                        db: Database,
                                        settings: Settings): Future[Either[Errors, Session]] =
    db.run(UsersRepo.findByEmail(req.email)).map {
      case Some(user) if user.isValidPassword(req.password) =>
        val timeNow = Instant.now()
        val token =
          Jwt.encode(user, settings.jwt.secret.value, timeNow, settings.jwt.sessionTtl.toSeconds)
        Right(Session(token))
      case _ => invalidEmailOrPassword
    }

  def find(token: String)(implicit ec: ExecutionContext,
                          db: Database,
                          settings: Settings): Future[Option[User]] =
    Jwt.decode(token, settings.jwt.secret.value) match {
      case Right(payload) => db.run(UsersRepo.findById(payload.id))
      case _              => Future.successful(None)
    }

  private lazy val invalidEmailOrPassword = Left(
    Errors(
      Seq(
        Errors.validation("invalid", "email"),
        Errors.validation("invalid", "password")
      )))
}
