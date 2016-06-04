package io.fcomb.persist

import com.github.t3hnar.bcrypt._
import io.fcomb.Db._
import io.fcomb.RichPostgresDriver.api._
import io.fcomb.models.{User, UserSignUpRequest, UserUpdateRequest}
import io.fcomb.validations._
import java.time.ZonedDateTime
import scala.concurrent.{ExecutionContext, Future}
import cats.data.Validated
import akka.http.scaladsl.util.FastFuture, FastFuture._

class UserTable(tag: Tag) extends Table[User](tag, "users") with PersistTableWithAutoLongPk {
  def email = column[String]("email")
  def username = column[String]("username")
  def fullName = column[Option[String]]("full_name")
  def passwordHash = column[String]("password_hash")
  def createdAt = column[ZonedDateTime]("created_at")
  def updatedAt = column[Option[ZonedDateTime]]("updated_at")

  def * =
    (id, email, username, fullName, passwordHash, createdAt, updatedAt) <>
      ((User.apply _).tupled, User.unapply)
}

object UsersRepo extends PersistModelWithAutoLongPk[User, UserTable] {
  val table = TableQuery[UserTable]

  def create(req: UserSignUpRequest)(implicit ec: ExecutionContext): Future[ValidationModel] = {
    val timeAt = ZonedDateTime.now()
    val user = mapModel(
      User(
        email = req.email,
        username = req.username,
        fullName = req.fullName,
        passwordHash = req.password.bcrypt(generateSalt),
        createdAt = timeAt,
        updatedAt = None
      )
    )
    validateThenApply(validate(userValidation(user, Some(req.password)))) {
      createDBIO(user)
    }
  }

  def update(id: Long, req: UserUpdateRequest)(implicit ec: ExecutionContext): Future[ValidationModel] =
    update(id)(_.copy(
      email = req.email,
      username = req.username,
      fullName = req.fullName,
      updatedAt = Some(ZonedDateTime.now())
    ))

  private val updatePasswordCompiled = Compiled { (userId: Rep[Long]) ⇒
    table.filter(_.id === userId).map { t ⇒
      (t.passwordHash, t.updatedAt)
    }
  }

  def updatePassword(
    userId:   Long,
    password: String
  )(implicit ec: ExecutionContext): Future[Boolean] = {
    val salt = generateSalt
    val passwordHash = password.bcrypt(salt)
    db.run {
      updatePasswordCompiled(userId)
        .update((passwordHash, Some(ZonedDateTime.now)))
        .map(_ == 1)
    }
  }

  def changePassword(
    user:        User,
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
  ): Future[Option[User]] = {
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
    user:        User,
    passwordOpt: Option[String]
  )(implicit ec: ExecutionContext) = {
    val plainValidations = validatePlain(
      "username" → List(
        lengthRange(user.username, 1, 255),
        notUuid(user.username)
      ),
      "email" → List(maxLength(user.email, 255), email(user.email))
    )
    val dbioValidations = validateDBIO(
      "username" → List(
        unique(uniqueUsernameCompiled(user.id, user.username))
      ),
      "email" → List(unique(uniqueEmailCompiled(user.id, user.email)))
    )
    passwordOpt match {
      case Some(password) ⇒
        (validatePassword(password) ::: plainValidations, dbioValidations)
      case None ⇒
        (plainValidations, dbioValidations)
    }
  }

  override def validate(user: User)(
    implicit
    ec: ExecutionContext
  ): ValidationDBIOResult =
    validate(userValidation(user, None))
}
