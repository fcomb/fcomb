package io.fcomb.services.user

import io.fcomb.services.Mandrill
import io.fcomb.Db.cache
import io.fcomb.models
import io.fcomb.persist
import io.fcomb.templates
import io.fcomb.utils.Random
import akka.stream.Materializer
import akka.actor.ActorSystem
import scala.concurrent.{ ExecutionContext, Future }
import scala.concurrent.duration._
import scalaz._, Scalaz._
import java.time.LocalDateTime
import java.util.UUID

object ResetPassword {
  private val ttl = Some(1.hour)

  // TODO: add email validation
  def reset(email: String)(implicit system: ActorSystem, materializer: Materializer) = {
    import system.dispatcher

    persist.User.findByEmail(email).flatMap {
      case Some(user) =>
        val token = Random.rand.alphanumeric.take(42).mkString
        val date = LocalDateTime.now.plusSeconds(ttl.get.toSeconds)
        cache.set(s"$prefix$token", user.id.toString, ttl).flatMap { _ =>
          val template = templates.ResetPassword(s"title: token $token", s"date: $date", token)
          Mandrill.sendTemplate(
            template.mandrillTemplateName,
            List(user.email),
            template.toHtml
          ).map(_ => user.success)
        }
      case None =>
        persist.User.validationErrorAsFuture("email", "not found")
    }
  }

  def setPassword(token: String, password: String)(implicit ec: ExecutionContext, materializer: Materializer) = {
    val key = s"$prefix$token"
    persist.User.validatePassword(password) match {
      case Success(_) =>
        cache.get(key).flatMap {
          case Some(id) =>
            val updateF = persist.User.updatePassword(UUID.fromString(id), password).map {
              case true  => ().successNel
              case false => persist.User.validationError("id", "not found")
            }
            for {
              res <- updateF
              _ <- cache.del(key)
            } yield res
          case _ =>
            persist.User.validationErrorAsFuture("token", "not found")
        }
      case e @ Failure(_) => Future.successful(e)
    }
  }

  private val prefix = "rp:"
}
