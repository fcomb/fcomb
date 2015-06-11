package io.fcomb.persist

import io.fcomb.Db._
import io.fcomb.models
import io.fcomb.macros._
import scalikejdbc._
import scala.concurrent.{ ExecutionContext, Future }
import java.util.UUID
import com.github.t3hnar.bcrypt._
import org.joda.time.DateTime

object User extends PersistModelWithPk[models.User, UUID] {
  override val tableName = "users"
  override val columns = Seq(
    "id", "username", "email", "full_name", "salt",
    "password_hash", "created_at", "updated_at"
  )

  implicit val mappable = materializeMappable[models.User]

  def apply(rn: ResultName[models.User])(rs: WrappedResultSet): models.User =
    autoConstruct(rs, rn)

  def create(
    email:    String,
    username: String,
    password: String,
    fullName: Option[String]
  )(implicit ec: ExecutionContext): Future[models.User] = {
    val timeAt = DateTime.now()
    create(models.User(
      id = UUID.randomUUID(),
      email = email,
      username = username,
      fullName = fullName,
      passwordHash = password.bcrypt(generateSalt),
      createdAt = timeAt,
      updatedAt = timeAt
    ))
  }
}
