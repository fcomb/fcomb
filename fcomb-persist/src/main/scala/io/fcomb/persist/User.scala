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
    validateWith(user, validationUserWithPassword(user, password)) {
      createDBIO(user)
    }
  }

  def update(id: UUID)(
    email:    String,
    username: String,
    fullName: Option[String]
  )(implicit ec: ExecutionContext): Future[ValidationModel] = findById(id).flatMap {
    case Some(user) => update(user.copy(
      email = email,
      username = username,
      fullName = fullName,
      updatedAt = LocalDateTime.now()
    ))
    case None => recordNotFoundAsFuture(id)
  }

  import Validations._

  def validationUserChain(user: models.User)(implicit ec: ExecutionContext) =
    user.email.is("email", present && isEmail) ::
      user.username.is("username", present)

  def validationUserWithPassword(user: models.User, password: String)(implicit ec: ExecutionContext) =
    validationUserChain(user) :: password.is("password", present)

  override def validate(user: models.User)(implicit ec: ExecutionContext) =
    validateChainAs(user, validationUserChain(user))
}
