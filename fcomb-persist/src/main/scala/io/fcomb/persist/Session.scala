package io.fcomb.persist

import io.fcomb.Db.cache
import io.fcomb.models
import io.fcomb.validations
import scala.concurrent.{ ExecutionContext, Future }
import scala.concurrent.duration._
import scala.util.Random
import scredis._
import shapeless._, contrib.scalaz._, syntax.std.function._
import scalaz._, scalaz.syntax.validation._
import org.apache.commons.codec.digest.DigestUtils
import java.security.SecureRandom
import java.util.UUID

object Session extends PersistTypes[models.Session] {
  val sessionIdLength = 42
  val random = new Random(new SecureRandom())
  val ttl = Some(30.days) // TODO: move into config

  def create(
    user: models.User
  )(
    implicit
    ec: ExecutionContext
  ): Future[ValidationModel] =
    createToken(userPrefix, user.id.toString)

  private def createToken(
    prefix: String,
    id:     String
  )(
    implicit
    ec: ExecutionContext
  ): Future[ValidationModel] = {
    val sessionId = s"${prefix}_${random.alphanumeric.take(sessionIdLength).mkString}"
    cache.set(getKey(sessionId), id, ttl).map { _ =>
      models.Session(sessionId).success[validations.ValidationMapResult]
    }
  }

  def findById(sessionId: String)(implicit ec: ExecutionContext) = {
    cache.get(getKey(sessionId)).flatMap {
      case Some(userId) if sessionId.startsWith(userPrefix) =>
        User.findById(UUID.fromString(userId))
      case _ =>
        Future.successful(None)
    }
  }

  def destroy(sessionId: String) = cache.del(getKey(sessionId))

  def isValid(sessionId: String) = sessionId.length == sessionIdLength

  @inline
  private def getKey(sessionId: String) =
    s"ses:${DigestUtils.sha1Hex(sessionId.take(sessionIdLength))}"

  private val userPrefix = "usr"

  private val appPrefix = "app"
}
