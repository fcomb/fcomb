/*
 * Copyright 2016 fcomb. <https://fcomb.io>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.fcomb.persist

import com.github.t3hnar.bcrypt._
import io.fcomb.Db._
import io.fcomb.RichPostgresDriver.api._
import io.fcomb.models.User
import io.fcomb.rpc.{UserSignUpRequest, UserUpdateRequest}
import io.fcomb.validations._
import java.time.ZonedDateTime
import scala.concurrent.{ExecutionContext, Future}
import cats.data.Validated
import akka.http.scaladsl.util.FastFuture, FastFuture._

class UserTable(tag: Tag) extends Table[User](tag, "users") with PersistTableWithAutoIntPk {
  def email        = column[String]("email")
  def username     = column[String]("username")
  def fullName     = column[Option[String]]("full_name")
  def passwordHash = column[String]("password_hash")
  def createdAt    = column[ZonedDateTime]("created_at")
  def updatedAt    = column[Option[ZonedDateTime]]("updated_at")

  def * =
    (id, email, username, fullName, passwordHash, createdAt, updatedAt) <>
      ((User.apply _).tupled, User.unapply)
}

object UsersRepo extends PersistModelWithAutoIntPk[User, UserTable] {
  val table = TableQuery[UserTable]

  def create(
      email: String,
      username: String,
      fullName: Option[String],
      password: String
  )(implicit ec: ExecutionContext): Future[ValidationModel] = {
    val timeAt = ZonedDateTime.now()
    val user = mapModel(
      User(
        id = None,
        email = email,
        username = username,
        fullName = fullName,
        passwordHash = password.bcrypt(generateSalt),
        createdAt = timeAt,
        updatedAt = None
      ))
    validateThenApply(validate(userValidation(user, Some(password)))) {
      createDBIO(user)
    }
  }

  def create(req: UserSignUpRequest)(implicit ec: ExecutionContext): Future[ValidationModel] = {
    val fullName = req.fullName match {
      case res @ Some(s) if s.nonEmpty => res
      case _                           => None
    }
    create(
      email = req.email,
      username = req.username,
      fullName = fullName,
      password = req.password
    )
  }

  def update(id: Int, req: UserUpdateRequest)(
      implicit ec: ExecutionContext): Future[ValidationModel] =
    update(id)(
      _.copy(
        email = req.email,
        username = req.username,
        fullName = req.fullName,
        updatedAt = Some(ZonedDateTime.now())
      ))

  private lazy val updatePasswordCompiled = Compiled { (userId: Rep[Int]) =>
    table.filter(_.id === userId).map { t =>
      (t.passwordHash, t.updatedAt)
    }
  }

  def updatePassword(
      userId: Int,
      password: String
  )(implicit ec: ExecutionContext): Future[Boolean] = {
    val salt         = generateSalt
    val passwordHash = password.bcrypt(salt)
    db.run {
      updatePasswordCompiled(userId).update((passwordHash, Some(ZonedDateTime.now))).map(_ == 1)
    }
  }

  def changePassword(
      user: User,
      oldPassword: String,
      newPassword: String
  )(implicit ec: ExecutionContext): Future[ValidationModel] = {
    if (oldPassword == newPassword)
      validationErrorAsFuture("password", "can't be the same")
    else if (user.isValidPassword(oldPassword))
      updatePassword(user.getId, newPassword).map(_ => Validated.Valid(user))
    else
      validationErrorAsFuture("password", "doesn't match")
  }

  private lazy val findByEmailCompiled = Compiled { email: Rep[String] =>
    table.filter(_.email === email).take(1)
  }

  def findByEmail(email: String) =
    db.run(findByEmailCompiled(email).result.headOption)

  private lazy val findByUsernameCompiled = Compiled { username: Rep[String] =>
    table.filter(_.username === username).take(1)
  }

  def matchByUsernameAndPassword(username: String, password: String)(
      implicit ec: ExecutionContext
  ): Future[Option[User]] = {
    val un = username.toLowerCase
    val q =
      if (un.indexOf('@') == -1) findByUsernameCompiled(un)
      else findByEmailCompiled(un)
    db.run(q.result.headOption).fast.map {
      case res @ Some(user) if user.isValidPassword(password) => res
      case _                                                  => None
    }
  }

  import Validations._

  private lazy val uniqueUsernameCompiled = Compiled {
    (id: Rep[Option[Int]], username: Rep[String]) =>
      notCurrentPkFilter(id).filter(_.username === username).exists
  }

  private lazy val uniqueEmailCompiled = Compiled { (id: Rep[Option[Int]], email: Rep[String]) =>
    notCurrentPkFilter(id).filter(_.email === email).exists
  }

  def validatePassword(password: String) =
    validatePlain(
      "password" -> List(lengthRange(password, 6, 50))
    )

  def userValidation(
      user: User,
      passwordOpt: Option[String]
  )(implicit ec: ExecutionContext) = {
    val plainValidations = validatePlain(
      "username" -> List(
        lengthRange(user.username, 1, 255),
        notUuid(user.username)
      ),
      "email" -> List(maxLength(user.email, 255), email(user.email))
    )
    val dbioValidations = validateDBIO(
      "username" -> List(
        unique(uniqueUsernameCompiled((user.id, user.username)))
      ),
      "email" -> List(unique(uniqueEmailCompiled((user.id, user.email))))
    )
    passwordOpt match {
      case Some(password) =>
        (validatePassword(password) ::: plainValidations, dbioValidations)
      case None =>
        (plainValidations, dbioValidations)
    }
  }

  override def validate(user: User)(
      implicit ec: ExecutionContext
  ): ValidationDBIOResult =
    validate(userValidation(user, None))
}
