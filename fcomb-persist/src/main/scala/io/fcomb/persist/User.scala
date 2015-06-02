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
}
