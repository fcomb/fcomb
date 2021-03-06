/*
 * Copyright 2017 fcomb. <https://fcomb.io>
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

import cats.data.Validated
import cats.syntax.eq._
import com.github.t3hnar.bcrypt._
import io.fcomb.PostgresProfile.api._
import io.fcomb.PostgresProfile.createJdbcMapping
import io.fcomb.models.{Pagination, PaginationData, User, UserRole}
import io.fcomb.models.common.Slug
import io.fcomb.persist.PaginationActions._
import io.fcomb.persist.acl.PermissionsRepo
import io.fcomb.rpc.{
  MemberUserIdRequest,
  MemberUserRequest,
  MemberUsernameRequest,
  UserCreateRequest,
  UserResponse,
  UserSignUpRequest,
  UserUpdateRequest
}
import io.fcomb.rpc.acl.PermissionUserMemberResponse
import io.fcomb.rpc.helpers.UserHelpers
import io.fcomb.utils.StringUtils
import io.fcomb.validation._
import java.time.OffsetDateTime
import scala.concurrent.ExecutionContext

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
  val label = "users"

  private def sortByPF(q: UserTable): PartialFunction[String, Rep[_]] = {
    case "id"        => q.id
    case "username"  => q.username
    case "fullName"  => q.fullName
    case "email"     => q.email
    case "role"      => q.role
    case "createdAt" => q.createdAt
    case "updatedAt" => q.updatedAt
  }

  private lazy val totalC = Compiled {
    table.length
  }

  def paginate(p: Pagination)(implicit ec: ExecutionContext): DBIO[PaginationData[UserResponse]] =
    for {
      groups <- sortPaginate(table, p)(sortByPF, _.username).result
      total  <- totalC.result
      data = groups.map(UserHelpers.response)
    } yield PaginationData(data, total = total, offset = p.offset, limit = p.limit)

  def create(
      email: String,
      username: String,
      fullName: Option[String],
      password: String,
      role: UserRole
  )(implicit ec: ExecutionContext): DBIO[ValidationModel] = {
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

  def create(req: UserSignUpRequest)(implicit ec: ExecutionContext): DBIO[ValidationModel] =
    create(
      email = req.email,
      username = req.username,
      fullName = StringUtils.trim(req.fullName),
      password = req.password,
      role = UserRole.User
    )

  def create(req: UserCreateRequest)(implicit ec: ExecutionContext): DBIO[ValidationModel] =
    create(
      email = req.email,
      username = req.username,
      fullName = StringUtils.trim(req.fullName),
      password = req.password,
      role = req.role
    )

  def update(slug: Slug, currentUser: User, req: UserUpdateRequest)(
      implicit ec: ExecutionContext): DBIO[ValidationModel] =
    find(slug).flatMap {
      case Some(user) =>
        if (user.id == currentUser.id && user.role =!= req.role)
          validationErrorDBIO("role", "Cannot downgrade yourself")
        else {
          val passwordHash = req.password.map(_.bcrypt(generateSalt)).getOrElse(user.passwordHash)
          val updated = user.copy(
            email = req.email,
            fullName = req.fullName,
            role = req.role,
            passwordHash = passwordHash,
            updatedAt = Some(OffsetDateTime.now())
          )
          val result = validate(userValidation(updated, req.password))
          validateThenApplyVMDBIO(result)(update(updated))
        }
      case _ => DBIO.successful(recordNotFound(slug))
    }

  private def recordNotFound[R](slug: Slug): ValidationResult[R] = {
    val (column, value) = slug match {
      case Slug.Id(id)         => ("id", id.toString)
      case Slug.Name(username) => ("username", username)
    }
    recordNotFound(column, value)
  }

  private lazy val updatePasswordC = Compiled { (userId: Rep[Int]) =>
    table.filter(_.id === userId).map { t =>
      (t.passwordHash, t.updatedAt)
    }
  }

  def updatePassword(userId: Int, password: String)(
      implicit ec: ExecutionContext): DBIO[ValidationResultUnit] =
    validatePassword(password) match {
      case Validated.Valid(_) =>
        val salt         = generateSalt
        val passwordHash = password.bcrypt(salt)
        updatePasswordC(userId).update((passwordHash, Some(OffsetDateTime.now))).map { res =>
          if (res != 0) Validated.Valid(())
          else validationError("id", "not found")
        }
      case res => DBIO.successful(res)
    }

  def changePassword(user: User, oldPassword: String, newPassword: String)(
      implicit ec: ExecutionContext): DBIO[ValidationModel] =
    if (oldPassword == newPassword)
      validationErrorDBIO("password", "can't be the same")
    else if (user.isValidPassword(oldPassword))
      updatePassword(user.getId(), newPassword).map(_ => Validated.Valid(user))
    else
      validationErrorDBIO("password", "doesn't match")

  private lazy val findByEmailC = Compiled { email: Rep[String] =>
    table.filter(_.email === email.asColumnOfType[String]("citext")).take(1)
  }

  def findByEmail(email: String) = findByEmailC(email).result.headOption

  private lazy val findByUsernameC = Compiled { username: Rep[String] =>
    table.filter(_.username === username.asColumnOfType[String]("citext")).take(1)
  }

  def findByUsernameDBIO(username: String) =
    findByUsernameC(username).result.headOption

  def find(slug: Slug): DBIOAction[Option[User], NoStream, Effect.Read] =
    slug match {
      case Slug.Id(id)     => findById(id)
      case Slug.Name(name) => findByUsernameDBIO(name)
    }

  def matchByUsernameAndPassword(username: String, password: String)(
      implicit ec: ExecutionContext): DBIO[Option[User]] = {
    val q =
      if (username.contains('@')) findByEmailC(username)
      else findByUsernameC(username)
    q.result.headOption.map {
      case res @ Some(user) if user.isValidPassword(password) => res
      case _                                                  => None
    }
  }

  def findByIdAsValidatedDBIO(id: Int)(implicit ec: ExecutionContext) =
    findById(id).map {
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

  def findAsValidatedDBIO(slug: Slug)(
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

  private lazy val findSuggestionsC = Compiled {
    (imageId: Rep[Int], userOwnerId: Rep[Int], username: Rep[String], limit: ConstColumn[Long]) =>
      findSuggestionsDBIO(imageId, username)
        .filter(_.id =!= userOwnerId)
        .map(t => (t.id, t.username, t.fullName))
        .take(limit)
  }

  def findSuggestions(imageId: Int, userOwnerId: Int, q: String, limit: Long = 16L)(
      implicit ec: ExecutionContext): DBIO[Seq[PermissionUserMemberResponse]] = {
    val username = s"$q%".trim
    findSuggestionsC((imageId, userOwnerId, username, limit)).result
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

  def destroy(slug: Slug, currentUser: User)(
      implicit ec: ExecutionContext): DBIO[ValidationResultUnit] =
    find(slug).flatMap {
      case Some(user) =>
        if (user.id == currentUser.id) validationErrorDBIO("id", "Cannot delete yourself")
        else super.destroy(user.getId())
      case _ => DBIO.successful(recordNotFound(slug))
    }

  import Validations._

  // TODO: check username format

  private lazy val uniqueUsernameC = Compiled { (id: Rep[Option[Int]], username: Rep[String]) =>
    exceptIdFilter(id).filter(_.username === username.asColumnOfType[String]("citext")).exists
  }

  private lazy val uniqueEmailC = Compiled { (id: Rep[Option[Int]], email: Rep[String]) =>
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
        unique(uniqueUsernameC((user.id, user.username)))
      ),
      "email" -> List(unique(uniqueEmailC((user.id, user.email))))
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
