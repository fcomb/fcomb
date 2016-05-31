package io.fcomb

import com.github.tminglei.slickpg.utils.SimpleArrayUtils
import com.github.tminglei.slickpg.PgEnumSupportUtils.sqlName
import io.fcomb.RichPostgresDriver._
import io.fcomb.models.{Enum, EnumItem}
import scala.reflect.ClassTag
import slick.ast.FieldSymbol
import slick.jdbc.JdbcType
import java.sql.{PreparedStatement, ResultSet}

package object persist {
  implicit val certificateKindColumnType = createEnumJdbcType(
    "certificate_kind", models.CertificateKind
  )

  implicit val nodeStateColumnType = createEnumJdbcType(
    "node_state", models.node.NodeState
  )

  implicit val tokenRoleColumnType = createEnumJdbcType(
    "token_role", models.TokenRole
  )

  implicit val tokenStateColumnType = createEnumJdbcType(
    "token_state", models.TokenState
  )

  implicit val applicationStateColumnType = createEnumJdbcType(
    "application_state", models.application.ApplicationState
  )

  implicit val containerStateColumnType = createEnumJdbcType(
    "container_state", models.docker.ContainerState
  )

  implicit val scaleStrategyKindColumnType = createEnumJdbcType(
    "scale_strategy_kind", models.application.ScaleStrategyKind
  )

  private def createEnumJdbcMapping[T <: EnumItem](
    sqlEnumTypeName: String,
    enum:            Enum[T],
    quoteName:       Boolean = false
  )(
    implicit
    tag: ClassTag[T]
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

  def createEnumListJdbcMapping[T <: EnumItem](
    sqlEnumTypeName: String,
    enumObject:      Enum[T],
    quoteName:       Boolean = false
  )(implicit tag: ClassTag[T]): JdbcType[List[T]] = {
    new AdvancedArrayJdbcType[T](
      sqlName(sqlEnumTypeName, quoteName),
      fromString = s ⇒ SimpleArrayUtils.fromString(s1 ⇒ enumObject.withName(s1))(s).orNull,
      mkString = v ⇒ SimpleArrayUtils.mkString[T](_.value)(v)
    ).to(_.toList)
  }

  implicit val dockerDistributionImageBlobStateColumnType = createEnumJdbcMapping(
    "dd_image_blob_state",
    models.docker.distribution.ImageBlobState
  )
}
