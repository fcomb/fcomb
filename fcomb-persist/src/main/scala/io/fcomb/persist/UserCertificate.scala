package io.fcomb.persist

import io.fcomb.RichPostgresDriver.api._
import io.fcomb.models
import scala.concurrent.{ExecutionContext, Future}
import java.time.ZonedDateTime

class UserCertificateTable(tag: Tag) extends Table[models.UserCertificate](tag, "user_certificates") {
  def userId = column[Long]("user_id")
  def certificate = column[Array[Byte]]("certificate")
  def key = column[Array[Byte]]("key")
  def password = column[Array[Byte]]("password")
  def createdAt = column[ZonedDateTime]("created_at")

  def * =
    (userId, certificate, key, password, createdAt) <>
      ((models.UserCertificate.apply _).tupled, models.UserCertificate.unapply)
}

object UserCertificate extends PersistModel[models.UserCertificate, UserCertificateTable] {
  val table = TableQuery[UserCertificateTable]

  def create(
    userId: Long,
    certificate: Array[Byte],
    key: Array[Byte],
    password: Array[Byte]
  )(implicit ec: ExecutionContext): Future[ValidationModel] =
    super.create(models.UserCertificate(
      userId = userId,
      certificate = certificate,
      key = key,
      password = password,
      createdAt = ZonedDateTime.now()
    ))
}
