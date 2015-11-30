package io.fcomb.persist

import com.github.t3hnar.bcrypt._
import io.fcomb.Db._
import io.fcomb.RichPostgresDriver.api._
import io.fcomb.models
import io.fcomb.validations._
import java.time.LocalDateTime
import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}
import scalaz._
import scalaz.Scalaz._
import slick.jdbc.GetResult

class UserTable(tag: Tag) extends Table[models.User](tag, "users") with PersistTableWithAutoLongPk {
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

object User extends PersistModelWithAutoLongPk[models.User, UserTable] {
  val table = TableQuery[UserTable]

  def create(
    email: String,
    username: String,
    password: String,
    fullName: Option[String]
  )(implicit ec: ExecutionContext): Future[ValidationModel] = {
    val timeAt = LocalDateTime.now()
    val user = mapModel(models.User(
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

  def updateByRequest(id: Long)(
    email: String,
    username: String,
    fullName: Option[String]
  )(implicit ec: ExecutionContext): Future[ValidationModel] =
    update(id)(_.copy(
      email = email,
      username = username,
      fullName = fullName,
      updatedAt = LocalDateTime.now()
    ))

  private val updatePasswordCompiled = Compiled { (userId: Rep[Long]) =>
    table
      .filter(_.id === userId)
      .map { t => (t.passwordHash, t.updatedAt) }
  }

  def updatePassword(
    userId: Long,
    password: String
  )(implicit ec: ExecutionContext): Future[Boolean] = {
    val salt = generateSalt
    val passwordHash = password.bcrypt(salt)
    db.run {
      updatePasswordCompiled(userId)
        .update((passwordHash, LocalDateTime.now))
        .map(_ == 1)
    }
  }

  def changePassword(
    user: models.User,
    oldPassword: String,
    newPassword: String
  )(implicit ec: ExecutionContext): Future[ValidationModel] = {
    if (oldPassword == newPassword)
      validationErrorAsFuture("password", "can't be the same")
    else if (user.isValidPassword(oldPassword))
      updatePassword(user.getId, newPassword).map(_ => user.success)
    else
      validationErrorAsFuture("password", "doesn't match")
  }

  private val findByEmailCompiled = Compiled { email: Rep[String] =>
    table.filter(_.email === email).take(1)
  }

  def findByEmail(email: String) =
    db.run(findByEmailCompiled(email).result.headOption)

  import Validations._

  private val uniqueUsernameCompiled = Compiled {
    (id: Rep[Option[Long]], username: Rep[String]) =>
      table.filter { f => f.id =!= id && f.username === username }.exists
  }

  private val uniqueEmailCompiled = Compiled {
    (id: Rep[Option[Long]], email: Rep[String]) =>
      table.filter { f => f.id =!= id && f.email === email }.exists
  }

  def validatePassword(password: String) =
    validatePlain(
      "password" -> List(lengthRange(password, 6, 50))
    )

  def userValidation(
    user: models.User,
    passwordOpt: Option[String]
  )(implicit ec: ExecutionContext) = {
    val plainValidations = validatePlain(
      "username" -> List(lengthRange(user.username, 1, 255), notUuid(user.username)),
      "email" -> List(maxLength(user.email, 255), email(user.email))
    )
    val dbioValidations = validateDBIO(
      "username" -> List(unique(uniqueUsernameCompiled(user.id, user.username))),
      "email" -> List(unique(uniqueEmailCompiled(user.id, user.email)))
    )
    passwordOpt match {
      case Some(password) =>
        (validatePassword(password) ::: plainValidations, dbioValidations)
      case None =>
        (plainValidations, dbioValidations)
    }
  }

  override def validate(user: models.User)(implicit ec: ExecutionContext): ValidationDBIOResult =
    validate(userValidation(user, None))
}
