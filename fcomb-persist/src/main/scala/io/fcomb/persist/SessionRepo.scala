package io.fcomb.persist

import io.fcomb.Db.redis
import io.fcomb.models.{Session, SessionCreateRequest, User}
import io.fcomb.validations
import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration._
import scala.util.Random
import redis._
import cats.data.Validated
import org.apache.commons.codec.digest.DigestUtils
import java.security.SecureRandom
import akka.http.scaladsl.util.FastFuture

object SessionRepo extends PersistTypes[Session] {
  val sessionIdLength = 42
  val random = new Random(new SecureRandom())
  val ttl = Some(30.days.toSeconds) // TODO: move into config

  def create(user: User)(
    implicit
    ec: ExecutionContext
  ): Future[ValidationModel] =
    createToken(prefix, user.getId.toString)

  private def createToken(prefix: String, id: String)(
    implicit
    ec: ExecutionContext
  ): Future[ValidationModel] = {
    val sessionId =
      s"$prefix${random.alphanumeric.take(sessionIdLength).mkString}"
    redis.set(getKey(sessionId), id, ttl).map { _ ⇒
      Validated.Valid(Session(sessionId))
    }
  }

  private val invalidEmailOrPassword = FastFuture.successful(
    validations.validationErrors(
      "email" → s"invalid",
      "password" → "invalid"
    )
  )

  def create(req: SessionCreateRequest)(
    implicit
    ec: ExecutionContext
  ): Future[ValidationModel] =
    UsersRepo.findByEmail(req.email).flatMap {
      case Some(user) if user.isValidPassword(req.password) ⇒ create(user)
      case _ ⇒ invalidEmailOrPassword
    }

  def findById(sessionId: String)(
    implicit
    ec: ExecutionContext
  ) = {
    redis.get(getKey(sessionId)).flatMap {
      case Some(userId) if sessionId.startsWith(prefix) ⇒
        UsersRepo.findByPk(userId.utf8String.toLong)
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
