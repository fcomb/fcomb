package io.fcomb.persist

import io.fcomb.Db.redis
import io.fcomb.models
import io.fcomb.request.SessionRequest
import io.fcomb.validations
import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration._
import scala.util.Random
import redis._
import shapeless._
import cats.data.Validated
import org.apache.commons.codec.digest.DigestUtils
import java.security.SecureRandom
import java.util.UUID
import akka.http.scaladsl.util.FastFuture

object Session extends PersistTypes[models.Session] {
  val sessionIdLength = 42
  val random = new Random(new SecureRandom())
  val ttl = Some(30.days.toSeconds) // TODO: move into config

  def create(
    user: models.User
  )(
    implicit
    ec: ExecutionContext
  ): Future[ValidationModel] =
    createToken(prefix, user.getId.toString)

  private def createToken(
    prefix: String,
    id:     String
  )(
    implicit
    ec: ExecutionContext
  ): Future[ValidationModel] = {
    val sessionId =
      s"$prefix${random.alphanumeric.take(sessionIdLength).mkString}"
    redis.set(getKey(sessionId), id, ttl).map { _ ⇒
      Validated.Valid(models.Session(sessionId))
    }
  }

  private val invalidEmailOrPassword = FastFuture.successful(
    validations.validationErrors(
      "email" → s"invalid",
      "password" → "invalid"
    )
  )

  def create(req: SessionRequest)(
    implicit
    ec: ExecutionContext
  ): Future[ValidationModel] =
    User.findByEmail(req.email).flatMap {
      case Some(user) if user.isValidPassword(req.password) ⇒
        create(user)
      case _ ⇒
        invalidEmailOrPassword
    }

  def findById(sessionId: String)(
    implicit
    ec: ExecutionContext
  ) = {
    redis.get(getKey(sessionId)).flatMap {
      case Some(userId) if sessionId.startsWith(prefix) ⇒
        User.findByPk(userId.utf8String.toLong)
      case _ ⇒
        FastFuture.successful(None)
    }
  }

  def destroy(sessionId: String) =
    redis.del(getKey(sessionId))

  @inline
  private def getKey(sessionId: String) =
    DigestUtils.sha1Hex(sessionId.take(sessionIdLength))

  val prefix = "ses_"
}
