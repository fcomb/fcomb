package io.fcomb.persist

import io.fcomb.Db._
import io.fcomb.models
import scalikejdbc._
import scala.concurrent.{ ExecutionContext, Future, blocking }
import java.util.UUID
import com.github.t3hnar.bcrypt._
import org.joda.time.DateTime

object User extends PersistModel[models.User] {
  override val tableName = "users"
  override val columns = Seq(
    "id", "username", "email", "full_name", "salt",
    "password_hash", "created_at", "updated_at"
  )

  def apply(p: SyntaxProvider[models.User])(rs: WrappedResultSet): models.User =
    apply(p.resultName)(rs)

  def apply(rn: ResultName[models.User])(rs: WrappedResultSet): models.User =
    autoConstruct(rs, rn)

  def create(
    email: String,
    username: String,
    password: String,
    fullName: Option[String]
  )(implicit ec: ExecutionContext): Future[models.User] = DB.futureLocalTx { implicit session =>
    Future {
      blocking {
        val salt = generateSalt
        val passwordHash = password.bcrypt(salt)
        val timeAt = DateTime.now()
        val id = UUID.randomUUID()
        withSQL {
          insert
            .into(User)
            .namedValues(
              column.id -> id,
              column.email -> email,
              column.username -> username,
              column.fullName -> fullName,
              column.passwordHash -> passwordHash,
              column.createdAt -> timeAt,
              column.updatedAt -> timeAt
            )
        }.update.apply()
        models.User(
          id = id,
          email = email,
          username = username,
          fullName = fullName,
          passwordHash = passwordHash,
          createdAt = timeAt,
          updatedAt = timeAt
        )
      }
    }
  }
}
