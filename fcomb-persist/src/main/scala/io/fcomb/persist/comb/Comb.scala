package io.fcomb.persist.comb

import io.fcomb.persist._
import io.fcomb.Db._
import io.fcomb.RichPostgresDriver.api._
import io.fcomb.models, models.comb
import io.fcomb.validations._
import java.time.LocalDateTime
import java.util.UUID
import scala.concurrent.{ ExecutionContext, Future }
import scalaz._
import scalaz.Scalaz._

class CombTable(tag: Tag) extends Table[comb.Comb](tag, "combs") with PersistTableWithUuidPk {
  def userId = column[UUID]("user_id")
  def name = column[String]("name")
  def slug = column[String]("slug")
  def createdAt = column[LocalDateTime]("created_at")
  def updatedAt = column[LocalDateTime]("updated_at")

  def * =
    (id, userId, name, slug, createdAt, updatedAt) <>
      ((comb.Comb.apply _).tupled, comb.Comb.unapply)
}

object Comb extends PersistModelWithUuid[comb.Comb, CombTable] {
  val table = TableQuery[CombTable]

  def create(
    userId: UUID,
    name: String,
    slug: Option[String]
  )(implicit ec: ExecutionContext): Future[ValidationModel] = {
    val timeAt = LocalDateTime.now()
    super.create(comb.Comb(
      id = UUID.randomUUID(),
      userId = userId,
      name = name,
      slug = slug.get, // TODO: orElse name to slug
      createdAt = timeAt,
      updatedAt = timeAt
    ))
  }

  // def update(id: UUID)(
  //   email:    String,
  //   username: String,
  //   fullName: Option[String]
  // )(implicit ec: ExecutionContext): Future[ValidationModel] =
  //   findById(id).flatMap {
  //     case Some(user) => update(user.copy(
  //       email = email,
  //       username = username,
  //       fullName = fullName,
  //       updatedAt = LocalDateTime.now()
  //     ))
  //     case None => recordNotFoundAsFuture(id)
  //   }

  // private val updatePasswordCompiled = Compiled { (userId: Rep[UUID]) =>
  //   table
  //     .filter(_.id === userId)
  //     .map { t => (t.passwordHash, t.updatedAt) }
  // }

  // def updatePassword(userId: UUID, password: String)(implicit ec: ExecutionContext): Future[Boolean] = {
  //   val salt = generateSalt
  //   val passwordHash = password.bcrypt(salt)
  //   db.run {
  //     updatePasswordCompiled(userId)
  //       .update((passwordHash, LocalDateTime.now))
  //       .map(_ == 1)
  //   }
  // }

  // def changePassword(
  //   user:        models.User,
  //   oldPassword: String,
  //   newPassword: String
  // )(implicit ec: ExecutionContext): Future[ValidationModel] = {
  //   if (oldPassword == newPassword)
  //     validationErrorAsFuture("password", "can't be the same")
  //   else if (user.isValidPassword(oldPassword))
  //     updatePassword(user.id, newPassword).map(_ => user.success)
  //   else
  //     validationErrorAsFuture("password", "doesn't match")
  // }

  // private val findByEmailCompiled = Compiled { email: Rep[String] =>
  //   table.filter(_.email === email).take(1)
  // }

  // def findByEmail(email: String) =
  //   db.run(findByEmailCompiled(email).result.headOption)

  // import Validations._

  // private val unqiueUsernameCompiled = Compiled {
  //   (id: Rep[UUID], username: Rep[String]) =>
  //     table.filter { f => f.id =!= id && f.username === username }.exists
  // }

  // private val uniqueEmailCompiled = Compiled {
  //   (id: Rep[UUID], email: Rep[String]) =>
  //     table.filter { f => f.id =!= id && f.email === email }.exists
  // }

  // def validatePassword(password: String) =
  //   validatePlain(
  //     "password" -> List(lengthRange(password, 6, 50))
  //   )

  // def userValidation(user: models.User, passwordOpt: Option[String])(implicit ec: ExecutionContext) = {
  //   val plainValidations = validatePlain(
  //     "username" -> List(present(user.username)),
  //     "email" -> List(present(user.email), email(user.email))
  //   )
  //   val dbioValidations = validateDBIO(
  //     "username" -> List(unique(unqiueUsernameCompiled(user.id, user.username))),
  //     "email" -> List(unique(uniqueEmailCompiled(user.id, user.email)))
  //   )
  //   passwordOpt match {
  //     case Some(password) =>
  //       (validatePassword(password) ::: plainValidations, dbioValidations)
  //     case None =>
  //       (plainValidations, dbioValidations)
  //   }
  // }

  // override def validate(user: models.User)(implicit ec: ExecutionContext): ValidationDBIOResult =
  //   validate(userValidation(user, None))
}
