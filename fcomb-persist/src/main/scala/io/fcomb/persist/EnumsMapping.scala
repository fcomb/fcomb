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

import com.github.tminglei.slickpg.PgEnumSupportUtils.sqlName
import com.github.tminglei.slickpg.utils.SimpleArrayUtils
import io.fcomb.RichPostgresDriver._
import io.fcomb.models.acl.{Action, Role, SourceKind, MemberKind}
import io.fcomb.models.docker.distribution.{ImageBlobState, ImageVisibilityKind, BlobFileState}
import io.fcomb.models.{ApplicationState, OwnerKind}
import io.fcomb.models.common.{Enum, EnumItem}
import java.sql.{PreparedStatement, ResultSet}
import scala.reflect.ClassTag
import slick.ast.FieldSymbol
import slick.jdbc.JdbcType

// TODO: keep only common mappings and move specific into own repo table
object EnumsMapping {
  implicit val applicationStateColumnType =
    createEnumJdbcMapping("application_state", ApplicationState)

  implicit val ownerKindColumnType = createEnumJdbcMapping("owner_kind", OwnerKind)

  implicit val aclActionColumnType = createEnumJdbcMapping("acl_action", Action)

  implicit val aclRoleColumnType = createEnumJdbcMapping("acl_role", Role)

  implicit val aclSourceKindColumnType = createEnumJdbcMapping("acl_source_kind", SourceKind)

  implicit val aclMemberKindColumnType = createEnumJdbcMapping("acl_member_kind", MemberKind)

  implicit val distributionImageBlobStateColumnType =
    createEnumJdbcMapping("dd_image_blob_state", ImageBlobState)

  implicit val distributionImageVisibilityKindColumnType =
    createEnumJdbcMapping("dd_image_visibility_kind", ImageVisibilityKind)

  implicit val blobFileStateColumnType = createEnumJdbcMapping("dd_blob_file_state", BlobFileState)

  private def createEnumJdbcMapping[T <: EnumItem](
      sqlEnumTypeName: String,
      enum: Enum[T],
      quoteName: Boolean = false
  )(
      implicit tag: ClassTag[T]
  ): JdbcType[T] = new DriverJdbcType[T] {
    override val classTag: ClassTag[T] = tag

    override def sqlType: Int = java.sql.Types.OTHER

    override def sqlTypeName(sym: Option[FieldSymbol]): String =
      sqlName(sqlEnumTypeName, quoteName)

    override def getValue(r: ResultSet, idx: Int): T = {
      val value = r.getString(idx)
      if (r.wasNull) null.asInstanceOf[T] else enum.withName(value)
    }

    override def setValue(v: T, p: PreparedStatement, idx: Int): Unit =
      p.setObject(idx, toStr(v), sqlType)

    override def updateValue(v: T, r: ResultSet, idx: Int): Unit =
      r.updateObject(idx, toStr(v), sqlType)

    override def hasLiteralForm: Boolean = true

    override def valueToSQLLiteral(v: T) =
      if (v == null) "NULL" else s"'${v.value}'"

    private def toStr(v: T) =
      if (v == null) null else v.value
  }

  private def createEnumListJdbcMapping[T <: EnumItem](
      sqlEnumTypeName: String,
      enumObject: Enum[T],
      quoteName: Boolean = false
  )(implicit tag: ClassTag[T]): JdbcType[List[T]] = {
    new AdvancedArrayJdbcType[T](
      sqlName(sqlEnumTypeName, quoteName),
      fromString = s => SimpleArrayUtils.fromString(s1 => enumObject.withName(s1))(s).orNull,
      mkString = v => SimpleArrayUtils.mkString[T](_.value)(v)
    ).to(_.toList)
  }
}
