package io.fcomb

import scalikejdbc._
import java.util.UUID
import java.sql.{ ResultSet, Array => SArray }

package object persist {
  implicit val uuidLevelTypeBinder: TypeBinder[UUID] =
    new TypeBinder[UUID] {
      def apply(rs: ResultSet, label: String) =
        UUID.fromString(rs.getString(label))

      def apply(rs: ResultSet, index: Int) =
        UUID.fromString(rs.getString(index))
    }
}
