package io.fcomb.services.user

import io.fcomb.Db.redis
import io.fcomb.services.Mandrill
import io.fcomb.models
import io.fcomb.persist
import io.fcomb.templates
import io.fcomb.validations.ValidationResultUnit
import io.fcomb.utils.Random
import akka.stream.Materializer
import akka.actor.ActorSystem
import scala.concurrent.{ ExecutionContext, Future }
import scala.concurrent.duration._
import scalaz._, Scalaz._
import java.time.LocalDateTime
import java.util.UUID
import redis._

object ResetPassword {
  private val ttl = Some(1.hour.toSeconds)

  // TODO: add email validation
  def reset(email: String)(
    implicit
    sys: ActorSystem,
    mat: Materializer
  ): Future[ValidationResultUnit] = {
    persist.User.findByEmail(email).flatMap {
      case Some(user) =>
        val token = Random.rand.alphanumeric.take(42).mkString
        val date = LocalDateTime.now.plusSeconds(ttl.get)
        redis.set(s"$prefix$token", user.id.toString, ttl).flatMap { _ =>
          val template = templates.ResetPassword(s"title: token $token", s"date: $date", token)
          Mandrill.sendTemplate(
            template.mandrillTemplateName,
            List(user.email),
            template.toHtml
          ).map(_ => ().success)
        }
      case None =>
        persist.User.validationErrorAsFuture("email", "not found")
    }
  }

  def set(token: String, password: String)(
    implicit
    ec: ExecutionContext,
    mat: Materializer
  ): Future[ValidationResultUnit] = {
    val key = s"$prefix$token"
    persist.User.validatePassword(password) match {
      case Success(_) =>
        redis.get(key).flatMap {
          case Some(id) =>
            val updateF = persist.User.updatePassword(UUID.fromString(id.utf8String), password).map {
              case true  => ().success
              case false => persist.User.validationError("id", "not found")
            }
            for {
              res <- updateF
              _ <- redis.del(key)
            } yield res
          case _ =>
            persist.User.validationErrorAsFuture("token", "not found")
        }
      case e @ Failure(_) => Future.successful(e)
    }
  }

  private val prefix = "rp:"
}
