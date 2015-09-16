package io.fcomb.models

import io.fcomb.models.comb._
import java.time.LocalDateTime
import java.util.UUID
import scala.language.implicitConversions

package object response {
  trait ModelServiceResponse

  implicit class ModelItem[T](val m: T) extends AnyRef {
    def toResponse[E <: ModelServiceResponse]()(implicit f: T => E) =
      f(m)
  }

  case class NoContentResponse() extends ModelServiceResponse

  case class UserResponse(
    id: Option[UUID],
    email: String,
    username: String,
    fullName: Option[String]
  ) extends ModelServiceResponse

  case class CombResponse(
    id: Option[Long],
    name: String,
    slug: String,
    createdAt: LocalDateTime,
    updatedAt: LocalDateTime
  ) extends ModelServiceResponse

  case class CombMethodResponse(
    id: Option[Long],
    combId: Long,
    kind: MethodKind.MethodKind,
    uri: String,
    endpoint: String,
    createdAt: LocalDateTime,
    updatedAt: LocalDateTime
  ) extends ModelServiceResponse

  case class SessionResponse(
    token: String
  ) extends ModelServiceResponse

  implicit def user2Response(u: User): UserResponse =
    UserResponse(
      id = u.id,
      email = u.email,
      username = u.username,
      fullName = u.fullName
    )

  implicit def session2Response(s: Session): SessionResponse =
    SessionResponse(s.token)

  implicit def comb2Response(c: comb.Comb): CombResponse =
    CombResponse(
      id = c.id,
      name = c.name,
      slug = c.slug,
      createdAt = c.createdAt,
      updatedAt = c.updatedAt
    )

  implicit def combMethod2Response(m: comb.CombMethod): CombMethodResponse =
    CombMethodResponse(
      id = m.id,
      combId = m.combId,
      kind = m.kind,
      uri = m.uri,
      endpoint = m.endpoint,
      createdAt = m.createdAt,
      updatedAt = m.updatedAt
    )
}
