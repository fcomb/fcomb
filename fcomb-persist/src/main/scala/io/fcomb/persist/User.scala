package io.fcomb.persist

import io.fcomb.Db._
import io.fcomb.models
import io.fcomb.validations._
import io.fcomb.RichPostgresDriver.api._
import scala.concurrent.{ ExecutionContext, Future }
import java.util.UUID
import com.github.t3hnar.bcrypt._
import java.time.LocalDateTime
import scalaz._, Scalaz._

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
    val user = mapModel(models.User(
      id = UUID.randomUUID(),
      email = email,
      username = username,
      fullName = fullName,
      passwordHash = password.bcrypt(generateSalt),
      createdAt = timeAt,
      updatedAt = timeAt
    ))
    validateThenApply(validate(userValidation(user, Some(password)))) {
      createDBIO(user)
    }
  }

  def update(id: UUID)(
    email:    String,
    username: String,
    fullName: Option[String]
  )(implicit ec: ExecutionContext): Future[ValidationModel] =
    findById(id).flatMap {
      case Some(user) => update(user.copy(
        email = email,
        username = username,
        fullName = fullName,
        updatedAt = LocalDateTime.now()
      ))
      case None => recordNotFoundAsFuture(id)
    }

  def updatePassword(userId: UUID, password: String)(implicit ec: ExecutionContext): Future[Boolean] = {
    val salt = generateSalt
    val passwordHash = password.bcrypt(salt)
    db.run {
      table
        .filter(_.id === userId)
        .map { t => (t.passwordHash, t.updatedAt) }
        .update((passwordHash, LocalDateTime.now))
        .map(_ == 1)
    }
  }

  def changePassword(user: models.User)(oldPassword: String, newPassword: String)(implicit ec: ExecutionContext): Future[ValidationModel] = {
    if (oldPassword == newPassword)
      validationErrorAsFuture("password", "same")
    else if (user.isValidPassword(oldPassword))
      updatePassword(user.id, newPassword).map(_ => user.success)
    else
      validationErrorAsFuture("password", "doesn't match")
  }

  private val findByEmailCompiled = Compiled { email: Rep[String] =>
    table.filter(_.email === email).take(1)
  }

  def findByEmail(email: String) =
    db.run(findByEmailCompiled(email).result.headOption)

  import Validations._

  private val unqiueUsernameCompiled = Compiled {
    (id: Rep[UUID], username: Rep[String]) =>
      table.filter { f => f.id =!= id && f.username === username }.exists
  }

  private val uniqueEmailCompiled = Compiled {
    (id: Rep[UUID], email: Rep[String]) =>
      table.filter { f => f.id =!= id && f.email === email }.exists
  }

  def userValidation(user: models.User, password: Option[String])(implicit ec: ExecutionContext) = {
    (
      validatePlain(
        "username" -> List(present(user.username)),
        "email" -> List(present(user.email), email(user.email))
      ),
        validateDBIO(
          "username" -> List(unique(unqiueUsernameCompiled(user.id, user.username))),
          "email" -> List(unique(uniqueEmailCompiled(user.id, user.email)))
        )
    )
  }

  override def validate(user: models.User)(implicit ec: ExecutionContext): ValidationDBIOResult =
    validate(userValidation(user, None))
}
