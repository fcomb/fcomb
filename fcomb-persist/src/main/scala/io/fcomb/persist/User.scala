package io.fcomb.persist

import com.github.t3hnar.bcrypt._
import io.fcomb.Db._
import io.fcomb.RichPostgresDriver.api._
import io.fcomb.models
import io.fcomb.validations._
import java.time.ZonedDateTime
import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}
import cats.data.Validated
import slick.jdbc.GetResult
import akka.http.scaladsl.util.FastFuture, FastFuture._

class UserTable(tag: Tag) extends Table[models.User](tag, "users") with PersistTableWithAutoLongPk {
  def email = column[String]("email")
  def username = column[String]("username")
  def fullName = column[Option[String]]("full_name")
  def passwordHash = column[String]("password_hash")
  def createdAt = column[ZonedDateTime]("created_at")
  def updatedAt = column[ZonedDateTime]("updated_at")

  def * =
    (id, email, username, fullName, passwordHash, createdAt, updatedAt) <>
      ((models.User.apply _).tupled, models.User.unapply)
}

object User extends PersistModelWithAutoLongPk[models.User, UserTable] {
  val table = TableQuery[UserTable]

  // TODO: for test only!!!
  def first() =
    db.run(table.take(1).result.head)

  def create(
    email:    String,
    username: String,
    password: String,
    fullName: Option[String]
  )(implicit ec: ExecutionContext): Future[ValidationModel] = {
    val timeAt = ZonedDateTime.now()
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
    }.flatMap(createCallbacks)
  }

  private def createCallbacks(res: ValidationModel)(
    implicit
    ec: ExecutionContext
  ) = res match {
    case s @ Validated.Valid(u)   ⇒ UserToken.createDefaults(u.getId).map(_ ⇒ s)
    case e @ Validated.Invalid(_) ⇒ Future.successful(e)
  }

  def updateByRequest(id: Long)(
    email:    String,
    username: String,
    fullName: Option[String]
  )(implicit ec: ExecutionContext): Future[ValidationModel] =
    update(id)(_.copy(
      email = email,
      username = username,
      fullName = fullName,
      updatedAt = ZonedDateTime.now()
    ))

  private val updatePasswordCompiled = Compiled { (userId: Rep[Long]) ⇒
    table
      .filter(_.id === userId)
      .map { t ⇒ (t.passwordHash, t.updatedAt) }
  }

  def updatePassword(
    userId:   Long,
    password: String
  )(implicit ec: ExecutionContext): Future[Boolean] = {
    val salt = generateSalt
    val passwordHash = password.bcrypt(salt)
    db.run {
      updatePasswordCompiled(userId)
        .update((passwordHash, ZonedDateTime.now))
        .map(_ == 1)
    }
  }

  def changePassword(
    user:        models.User,
    oldPassword: String,
    newPassword: String
  )(implicit ec: ExecutionContext): Future[ValidationModel] = {
    if (oldPassword == newPassword)
      validationErrorAsFuture("password", "can't be the same")
    else if (user.isValidPassword(oldPassword))
      updatePassword(user.getId, newPassword).map(_ ⇒ Validated.Valid(user))
    else
      validationErrorAsFuture("password", "doesn't match")
  }

  private val findByTokenCompiled = Compiled {
    (token: Rep[String], role: Rep[models.TokenRole.TokenRole]) ⇒
      table
        .joinLeft(UserToken.table).on(_.id === _.userId)
        .filter { q ⇒
          q._2.map(_.token) === token &&
            q._2.map(_.role) === role &&
            q._2.map(_.state) === models.TokenState.Enabled
        }
        .map(_._1)
        .take(1)
  }

  def findByToken(token: String, role: models.TokenRole.TokenRole) =
    db.run(findByTokenCompiled(token, role).result.headOption)

  private val findByEmailCompiled = Compiled { email: Rep[String] ⇒
    table.filter(_.email === email).take(1)
  }

  def findByEmail(email: String) =
    db.run(findByEmailCompiled(email).result.headOption)

  private val findByUsernameCompiled = Compiled { username: Rep[String] ⇒
    table.filter(_.username === username).take(1)
  }

  def matchByUsernameAndPassword(username: String, password: String)(
    implicit
    ec: ExecutionContext
  ): Future[Option[models.User]] = {
    val un = username.toLowerCase
    val q =
      if (un.indexOf('@') == -1) findByUsernameCompiled(un)
      else findByEmailCompiled(un)
    db.run(q.result.headOption).fast.map {
      case res @ Some(user) if user.isValidPassword(password) ⇒ res
      case _ ⇒ None
    }
  }

  import Validations._

  private val uniqueUsernameCompiled = Compiled {
    (id: Rep[Option[Long]], username: Rep[String]) ⇒
      notCurrentPkFilter(id).filter(_.username === username).exists
  }

  private val uniqueEmailCompiled = Compiled {
    (id: Rep[Option[Long]], email: Rep[String]) ⇒
      notCurrentPkFilter(id).filter(_.email === email).exists
  }

  def validatePassword(password: String) =
    validatePlain(
      "password" → List(lengthRange(password, 6, 50))
    )

  def userValidation(
    user:        models.User,
    passwordOpt: Option[String]
  )(implicit ec: ExecutionContext) = {
    val plainValidations = validatePlain(
      "username" → List(lengthRange(user.username, 1, 255), notUuid(user.username)),
      "email" → List(maxLength(user.email, 255), email(user.email))
    )
    val dbioValidations = validateDBIO(
      "username" → List(unique(uniqueUsernameCompiled(user.id, user.username))),
      "email" → List(unique(uniqueEmailCompiled(user.id, user.email)))
    )
    passwordOpt match {
      case Some(password) ⇒
        (validatePassword(password) ::: plainValidations, dbioValidations)
      case None ⇒
        (plainValidations, dbioValidations)
    }
  }

  override def validate(user: models.User)(implicit ec: ExecutionContext): ValidationDBIOResult =
    validate(userValidation(user, None))
}
