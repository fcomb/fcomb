package io.fcomb.persist

import akka.stream.Materializer
import io.fcomb.Db.db
import io.fcomb.RichPostgresDriver.api._
import io.fcomb.models, models.{TokenRole, TokenState}
import io.fcomb.request
import io.fcomb.persist._
import io.fcomb.validations._
import io.fcomb.utils.Random.random
import java.time.LocalDateTime
import scala.concurrent.{ExecutionContext, Future}
import java.time.ZonedDateTime
import java.util.UUID

class UserTokenTable(tag: Tag) extends Table[models.UserToken](tag, "user_tokens") {
  def token = column[String]("token")
  def role = column[TokenRole.TokenRole]("role")
  def state = column[TokenState.TokenState]("state")
  def userId = column[Long]("user_id")
  def createdAt = column[ZonedDateTime]("created_at")
  def updatedAt = column[ZonedDateTime]("updated_at")

  def * =
    (token, role, state, userId, createdAt, updatedAt) <>
      ((models.UserToken.apply _).tupled, models.UserToken.unapply)
}

object UserToken extends PersistModel[models.UserToken, UserTokenTable] {
  val table = TableQuery[UserTokenTable]
  val prefix = "tkn_"
  val defaultLength = 96

  def createDefaults(userId: Long) = {
    val timeNow = ZonedDateTime.now()
    val tokens = Seq(
      models.UserToken(
        token = generateToken(),
        role = models.TokenRole.Api,
        state = models.TokenState.Enabled,
        userId = userId,
        createdAt = timeNow,
        updatedAt = timeNow
      ),
      models.UserToken(
        token = generateToken(),
        role = models.TokenRole.JoinCluster,
        state = models.TokenState.Enabled,
        userId = userId,
        createdAt = timeNow,
        updatedAt = timeNow
      )
    )
    db.run(createDBIO(tokens))
  }

  private def generateToken() =
    s"$prefix${random.alphanumeric.take(defaultLength).mkString}"

  private val findAllByUserIdCompiled = Compiled { userId: Rep[Long] ⇒
    table.filter(_.userId === userId)
  }

  def findAllByUserId(userId: Long) =
    db.run(findAllByUserIdCompiled(userId).result)
}
