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

import akka.http.scaladsl.util.FastFuture, FastFuture._
import cats.data.Validated
import com.github.t3hnar.bcrypt._
import io.fcomb.Db._
import io.fcomb.PostgresProfile.api._
import io.fcomb.PostgresProfile.createJdbcMapping
import io.fcomb.models.{User, UserRole}
import io.fcomb.models.common.Slug
import io.fcomb.persist.acl.PermissionsRepo
import io.fcomb.rpc.{
  MemberUserIdRequest,
  MemberUserRequest,
  MemberUsernameRequest,
  UserSignUpRequest,
  UserUpdateRequest
}
import io.fcomb.rpc.acl.PermissionUserMemberResponse
import io.fcomb.validation._
import java.time.OffsetDateTime
import scala.concurrent.{ExecutionContext, Future}

object UserTableImplicits {
  implicit val userRoleColumnType = createJdbcMapping("user_role", UserRole)
}
import UserTableImplicits._

final class UserTable(tag: Tag) extends Table[User](tag, "users") with PersistTableWithAutoIntPk {
  def email        = column[String]("email")
  def username     = column[String]("username")
  def fullName     = column[Option[String]]("full_name")
  def passwordHash = column[String]("password_hash")
  def role         = column[UserRole]("role")
  def createdAt    = column[OffsetDateTime]("created_at")
  def updatedAt    = column[Option[OffsetDateTime]]("updated_at")

  def * =
    (id.?, email, username, fullName, passwordHash, role, createdAt, updatedAt) <>
      ((User.apply _).tupled, User.unapply)
}

object UsersRepo extends PersistModelWithAutoIntPk[User, UserTable] {
  val table = TableQuery[UserTable]

  def create(
      email: String,
      username: String,
      fullName: Option[String],
      password: String,
      role: UserRole
  )(implicit ec: ExecutionContext): Future[ValidationModel] = {
    val timeAt = OffsetDateTime.now()
    val user = mapModel(
      User(
        id = None,
        email = email,
        username = username,
        fullName = fullName,
        passwordHash = password.bcrypt(generateSalt),
        role = role,
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
      password = req.password,
      role = UserRole.Developer
    )
  }

  def update(id: Int, req: UserUpdateRequest)(
      implicit ec: ExecutionContext): Future[ValidationModel] =
    update(id)(
      _.copy(
        email = req.email,
        fullName = req.fullName,
        updatedAt = Some(OffsetDateTime.now())
      ))

  private lazy val updatePasswordCompiled = Compiled { (userId: Rep[Int]) =>
    table.filter(_.id === userId).map { t =>
      (t.passwordHash, t.updatedAt)
    }
  }

  def updatePassword(userId: Int, password: String)(
      implicit ec: ExecutionContext): Future[ValidationResultUnit] =
    validatePassword(password) match {
      case Validated.Valid(_) =>
        val salt         = generateSalt
        val passwordHash = password.bcrypt(salt)
        db.run {
            updatePasswordCompiled(userId)
              .update((passwordHash, Some(OffsetDateTime.now)))
              .map(_ != 0)
          }
          .fast
          .map { isUpdated =>
            if (isUpdated) Validated.Valid(())
            else validationError("id", "not found")
          }
      case res => FastFuture.successful(res)
    }

  def changePassword(
      user: User,
      oldPassword: String,
      newPassword: String
  )(implicit ec: ExecutionContext): Future[ValidationModel] =
    if (oldPassword == newPassword)
      validationErrorAsFuture("password", "can't be the same")
    else if (user.isValidPassword(oldPassword))
      updatePassword(user.getId(), newPassword).map(_ => Validated.Valid(user))
    else
      validationErrorAsFuture("password", "doesn't match")

  private lazy val findByEmailCompiled = Compiled { email: Rep[String] =>
    table.filter(_.email === email.asColumnOfType[String]("citext")).take(1)
  }

  def findByEmail(email: String) =
    db.run(findByEmailCompiled(email).result.headOption)

  private lazy val findByUsernameCompiled = Compiled { username: Rep[String] =>
    table.filter(_.username === username.asColumnOfType[String]("citext")).take(1)
  }

  def findByUsernameDBIO(username: String) =
    findByUsernameCompiled(username).result.headOption

  def findBySlugDBIO(slug: Slug): DBIOAction[Option[User], NoStream, Effect.Read] =
    slug match {
      case Slug.Id(id)     => findByIdDBIO(id)
      case Slug.Name(name) => findByUsernameDBIO(name)
    }

  def findBySlug(slug: Slug) =
    db.run(findBySlugDBIO(slug))

  def matchByUsernameAndPassword(username: String, password: String)(
      implicit ec: ExecutionContext): Future[Option[User]] = {
    val q =
      if (username.contains('@')) findByEmailCompiled(username)
      else findByUsernameCompiled(username)
    db.run(q.result.headOption).fast.map {
      case res @ Some(user) if user.isValidPassword(password) => res
      case _                                                  => None
    }
  }

  def findByIdAsValidatedDBIO(id: Int)(implicit ec: ExecutionContext) =
    findByIdDBIO(id).map {
      case Some(u) => Validated.Valid(u)
      case _       => validationError("member.id", "Not found")
    }

  def findByUsernameAsValidatedDBIO(username: String)(implicit ec: ExecutionContext) =
    findByUsernameDBIO(username).map {
      case Some(u) => Validated.Valid(u)
      case _       => validationError("member.username", "Not found")
    }

  def findByMemberRequestAsValidatedDBIO(req: MemberUserRequest)(
      implicit ec: ExecutionContext): DBIOAction[ValidationResult[User], NoStream, Effect.Read] =
    req match {
      case MemberUserIdRequest(id)     => findByIdAsValidatedDBIO(id)
      case MemberUsernameRequest(name) => findByUsernameAsValidatedDBIO(name)
    }

  def findBySlugAsValidatedDBIO(slug: Slug)(
      implicit ec: ExecutionContext): DBIOAction[ValidationResult[User], NoStream, Effect.Read] =
    slug match {
      case Slug.Id(id)     => findByIdAsValidatedDBIO(id)
      case Slug.Name(name) => findByUsernameAsValidatedDBIO(name)
    }

  def findSuggestionsDBIO(imageId: Rep[Int], username: Rep[String]) =
    table.filter { t =>
      t.username.like(username.asColumnOfType[String]("citext")) &&
      !t.id.in(PermissionsRepo.findUserMemberIdsByImageIdDBIO(imageId))
    }

  private lazy val findSuggestionsCompiled = Compiled {
    (imageId: Rep[Int], userOwnerId: Rep[Int], username: Rep[String], limit: ConstColumn[Long]) =>
      findSuggestionsDBIO(imageId, username)
        .filter(_.id =!= userOwnerId)
        .map(t => (t.id, t.username, t.fullName))
        .take(limit)
  }

  def findSuggestions(imageId: Int, userOwnerId: Int, q: String, limit: Long = 16L)(
      implicit ec: ExecutionContext): Future[Seq[PermissionUserMemberResponse]] = {
    val username = s"$q%".trim
    db.run(findSuggestionsCompiled((imageId, userOwnerId, username, limit)).result)
      .fast
      .map(_.map {
        case (id, username, fullName) =>
          PermissionUserMemberResponse(
            id = id,
            isOwner = false,
            username = username,
            fullName = fullName
          )
      })
  }

  import Validations._

  // TODO: check username format

  private lazy val uniqueUsernameCompiled = Compiled {
    (id: Rep[Option[Int]], username: Rep[String]) =>
      exceptIdFilter(id).filter(_.username === username.asColumnOfType[String]("citext")).exists
  }

  private lazy val uniqueEmailCompiled = Compiled { (id: Rep[Option[Int]], email: Rep[String]) =>
    exceptIdFilter(id).filter(_.email === email.asColumnOfType[String]("citext")).exists
  }

  def validatePassword(password: String) =
    validatePlain("password" -> List(lengthRange(password, 6, 50)))

  def userValidation(user: User, passwordOpt: Option[String])(implicit ec: ExecutionContext) = {
    val plainValidations = validatePlain(
      "username" -> List(
        lengthRange(user.username, 1, 255),
        matches(user.username, User.nameRegEx, "invalid name format")
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

  override def validate(user: User)(implicit ec: ExecutionContext): ValidationDBIOResult =
    validate(userValidation(user, None))
}
