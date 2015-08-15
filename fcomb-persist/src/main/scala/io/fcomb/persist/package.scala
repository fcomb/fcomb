package io.fcomb

import io.fcomb.RichPostgresDriver._
// import scalikejdbc._
import java.util.UUID
import java.sql.{ ResultSet, Array => SArray, Timestamp }
import java.time._
import slick.jdbc.GetResult

package object persist {
  // implicit val dateTimeTypeBinder: TypeBinder[LocalDateTime] =
  //   new TypeBinder[LocalDateTime] {
  //     def apply(rs: ResultSet, label: String) =
  //       LocalDateTime.parse(rs.getString(label))

  //     def apply(rs: ResultSet, index: Int) =
  //       LocalDateTime.parse(rs.getString(index))
  //   }

  // implicit val uuidLevelTypeBinder: TypeBinder[UUID] =
  //   new TypeBinder[UUID] {
  //     def apply(rs: ResultSet, label: String) =
  //       UUID.fromString(rs.getString(label))

  //     def apply(rs: ResultSet, index: Int) =
  //       UUID.fromString(rs.getString(index))
  //   }

  implicit val methodKindColumnType = createEnumJdbcType("method_kind", models.comb.MethodKind)

  import io.fcomb.RichPostgresDriver.api._

  implicit val localDateTimeType =
    MappedColumnType.base[LocalDateTime, Timestamp](Timestamp.valueOf, _.toLocalDateTime)

  implicit val uuidResult = GetResult(r => UUID.fromString(r.<<))

  implicit def listResult[T]: GetResult[List[T]] = GetResult(r => {
    r.rs.getArray(r.skip.currentPos)
      .getArray
      .asInstanceOf[Array[T]]
      .filterNot(_ == null)
      .toList
  })
}
