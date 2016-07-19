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

import io.fcomb.Db.redis
import io.fcomb.persist.UsersRepo
import io.fcomb.templates
import io.fcomb.validations.ValidationResultUnit
import io.fcomb.utils.Random
import akka.stream.Materializer
import akka.actor.ActorSystem
import akka.http.scaladsl.util.FastFuture
import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration._
import cats.data.Validated
import java.time.LocalDateTime
import redis._

object ResetPassword {
  private val ttl = Some(1.hour.toSeconds)

  // TODO: add email validation
  def reset(email: String)(
      implicit sys: ActorSystem,
      mat: Materializer
  ): Future[ValidationResultUnit] = {
    UsersRepo.findByEmail(email).flatMap {
      case Some(user) =>
        val token = Random.random.alphanumeric.take(42).mkString
        val date  = LocalDateTime.now.plusSeconds(ttl.get)
        redis.set(s"$prefix$token", user.id.toString, ttl).flatMap { _ =>
          val template = templates.ResetPassword(
            s"title: token $token",
            s"date: $date",
            token
          )
          ???
        }
      case None =>
        UsersRepo.validationErrorAsFuture("email", "not found")
    }
  }

  def set(token: String, password: String)(
      implicit ec: ExecutionContext,
      mat: Materializer
  ): Future[ValidationResultUnit] = {
    val key = s"$prefix$token"
    UsersRepo.validatePassword(password) match {
      case Validated.Valid(_) =>
        redis.get(key).flatMap {
          case Some(id) =>
            val updateF = UsersRepo.updatePassword(id.utf8String.toInt, password).map {
              isUpdated =>
                if (isUpdated) Validated.Valid(())
                else UsersRepo.validationError("id", "not found")
            }
            for {
              res <- updateF
              _   <- redis.del(key)
            } yield res
          case _ =>
            UsersRepo.validationErrorAsFuture("token", "not found")
        }
      case e @ Validated.Invalid(_) => FastFuture.successful(e)
    }
  }

  private val prefix = "rp:"
}
