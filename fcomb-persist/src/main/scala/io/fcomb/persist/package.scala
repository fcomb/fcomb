package io.fcomb

import io.fcomb.RichPostgresDriver._

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

  implicit val blobStateColumnType =
    createEnumJdbcType("blob_state", models.docker.distribution.BlobState)
}
