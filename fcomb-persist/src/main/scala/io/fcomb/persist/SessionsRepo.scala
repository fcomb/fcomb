package io.fcomb.persist

import io.fcomb.Db.redis
import io.fcomb.models.{Session, SessionCreateRequest, User}
import io.fcomb.models.errors.{FailureResponse, ValidationException}
import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration._
import scala.util.Random
import redis._
import cats.data.Xor
import org.apache.commons.codec.digest.DigestUtils
import java.security.SecureRandom
import akka.http.scaladsl.util.FastFuture, FastFuture._

object SessionsRepo {
  val sessionIdLength = 42
  val random = new Random(new SecureRandom())
  val ttl = Some(30.days.toSeconds) // TODO: move into config

  private def createToken(prefix: String, id: String)(
    implicit
    ec: ExecutionContext
  ): Future[Xor[FailureResponse, Session]] = {
    val sessionId =
      s"$prefix${random.alphanumeric.take(sessionIdLength).mkString}"
    redis.set(getKey(sessionId), id, ttl)
      .fast
      .map(_ ⇒ Xor.right(Session(sessionId)))
  }

  private val invalidEmailOrPassword = FastFuture.successful(
    Xor.left(FailureResponse.fromExceptions(Seq(
      ValidationException("email", "invalid"),
      ValidationException("password", "invalid")
    )))
  )

  def create(req: SessionCreateRequest)(
    implicit
    ec: ExecutionContext
  ): Future[Xor[FailureResponse, Session]] =
    UsersRepo.findByEmail(req.email).flatMap {
      case Some(user) if user.isValidPassword(req.password) ⇒
        createToken(prefix, user.getId.toString)
      case _ ⇒ invalidEmailOrPassword
    }

  def findById(sessionId: String)(
    implicit
    ec: ExecutionContext
  ): Future[Option[User]] = {
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
