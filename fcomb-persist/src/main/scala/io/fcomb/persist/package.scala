package io.fcomb

import io.fcomb.RichPostgresDriver._
import java.util.UUID
import java.sql.{ResultSet, Array ⇒ SArray, Timestamp}
import java.time.{LocalDateTime, ZonedDateTime, ZoneId}
import java.net.InetAddress
import slick.jdbc.GetResult
import scala.collection.JavaConversions._

package object persist {
  implicit val certificateKindColumnType =
    createEnumJdbcType("certificate_kind", models.CertificateKind)

  implicit val nodeStateColumnType =
    createEnumJdbcType("node_state", models.node.NodeState)

  implicit val tokenRoleColumnType =
    createEnumJdbcType("token_role", models.TokenRole)

  implicit val tokenStateColumnType =
    createEnumJdbcType("token_state", models.TokenState)

  implicit val applicationStateColumnType =
    createEnumJdbcType("application_state", models.application.ApplicationState)

  implicit val containerStateColumnType =
    createEnumJdbcType("container_state", models.docker.ContainerState)

  implicit val scaleStrategyKindColumnType =
    createEnumJdbcType("scale_strategy_kind", models.application.ScaleStrategyKind)

  import io.fcomb.RichPostgresDriver.api._

  implicit val localDateTimeType =
    MappedColumnType.base[LocalDateTime, Timestamp](Timestamp.valueOf, _.toLocalDateTime)

  implicit val zonedDateTimeType =
    MappedColumnType.base[ZonedDateTime, Timestamp](
      d ⇒ Timestamp.valueOf(d.toLocalDateTime),
      _.toLocalDateTime.atZone(ZoneId.systemDefault())
    )

  implicit val uuidResult = GetResult(r ⇒ UUID.fromString(r.<<))

  implicit def listResult[T]: GetResult[List[T]] =
    GetResult { r ⇒
      Option(r.rs.getArray(r.skip.currentPos))
        .map(_.getArray
          .asInstanceOf[Array[T]]
          .filterNot(_ == null)
          .toList)
        .getOrElse(List.empty)
    }

  implicit def mapResult: GetResult[Map[String, String]] = GetResult(r ⇒ {
    r.rs.getObject(r.skip.currentPos)
      .asInstanceOf[java.util.Map[String, String]]
      .toMap
      .filterNot(_._1 == null)
  })
}
