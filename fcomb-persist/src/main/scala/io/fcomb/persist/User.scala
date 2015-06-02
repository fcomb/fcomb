package io.fcomb.persist

import io.fcomb.models
import scalikejdbc._
import scala.concurrent.ExecutionContext
import java.util.UUID
import io.fcomb.Db._

object User extends PersistModel[models.User] {
  override val tableName = "users"
  override val columns = Seq(
    "id", "username", "email", "full_name", "salt",
    "password_hash", "created_at", "updated_at"
  )

  def apply(p: SyntaxProvider[models.User])(rs: WrappedResultSet): models.User =
    apply(p.resultName)(rs)

  def apply(rn: ResultName[models.User])(rs: WrappedResultSet): models.User =
    models.User(
      id = rs.get(rn.id),
      username = rs.get(rn.username),
      email = rs.get(rn.email),
      fullName = rs.get(rn.fullName),
      salt = rs.get(rn.salt),
      passwordHash = rs.get(rn.passwordHash),
      createdAt = rs.get(rn.createdAt),
      updatedAt = rs.get(rn.updatedAt)
    )

  // def applyWithRights(rs: WrappedResultSet) = {
  //   val rights = rs.arrayOpt("rights").map(
  //     _.getArray().asInstanceOf[Array[String]].toList.map(models.RoleType.withName)
  //   ).getOrElse(List.empty)
  //   apply(rs, rights)
  // }
  //
  // // TODO: paginate
  // def all()(implicit ec: ExecutionContext) = asyncJdbc {
  //   sql"""
  //   select users.*, array_agg_custom(roles.rights) as rights
  //   from users
  //   left join roles_users on roles_users.user_id = users.id
  //   left join roles on roles_users.role_id = roles.id
  //   group by users.id
  //   """.stripMargin.map(applyWithRights).list.apply()
  // }
}
