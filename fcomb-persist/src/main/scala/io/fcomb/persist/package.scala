package io.fcomb

import scalikejdbc._
import java.util.UUID
import java.sql.{ ResultSet, Array => SArray, Timestamp }
import java.time._

package object persist {
  implicit val dateTimeTypeBinder: TypeBinder[LocalDateTime] =
    new TypeBinder[LocalDateTime] {
      def apply(rs: ResultSet, label: String) =
        LocalDateTime.parse(rs.getString(label))

      def apply(rs: ResultSet, index: Int) =
        LocalDateTime.parse(rs.getString(index))
    }

  implicit val uuidLevelTypeBinder: TypeBinder[UUID] =
    new TypeBinder[UUID] {
      def apply(rs: ResultSet, label: String) =
        UUID.fromString(rs.getString(label))

      def apply(rs: ResultSet, index: Int) =
        UUID.fromString(rs.getString(index))
    }

  import io.fcomb.RichPostgresDriver.api._

  implicit val localDateTimeType =
    MappedColumnType.base[LocalDateTime, Timestamp](Timestamp.valueOf, _.toLocalDateTime)

}
