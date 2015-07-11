package io.fcomb.persist

import io.fcomb.Db._
import io.fcomb.models
import io.fcomb.RichPostgresDriver.api._
import scala.concurrent.{ ExecutionContext, Future }
import java.util.UUID
import com.github.t3hnar.bcrypt._
import java.time.LocalDateTime

class UserTable(tag: Tag) extends Table[models.User](tag, "users") with PersistTableWithUuidPk {
  def email = column[String]("email")
  def username = column[String]("username")
  def fullName = column[Option[String]]("full_name")
  def passwordHash = column[String]("password_hash")
  def createdAt = column[LocalDateTime]("created_at")
  def updatedAt = column[LocalDateTime]("updated_at")

  def * =
    (id, email, username, fullName, passwordHash, createdAt, updatedAt) <>
      ((models.User.apply _).tupled, models.User.unapply)
}

object User extends PersistModelWithUuid[models.User, UserTable] {
  val table = TableQuery[UserTable]

  def create(
    email:    String,
    username: String,
    password: String,
    fullName: Option[String]
  )(implicit ec: ExecutionContext): Future[ValidationModel] = {
    val timeAt = LocalDateTime.now()
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
