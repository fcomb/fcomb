package io.fcomb.persist

import io.fcomb.RichPostgresDriver.api._
import io.fcomb.models
import io.fcomb.validations.ValidationResultUnit
import scalaz._, Scalaz._
import scala.concurrent.{ExecutionContext, Future}
import java.time.ZonedDateTime

class UserCertificateTable(tag: Tag) extends Table[models.UserCertificate](tag, "user_certificates") {
  def userId = column[Long]("user_id")
  def kind = column[models.UserCertificateKind.UserCertificateKind]("kind")
  def certificate = column[Array[Byte]]("certificate")
  def key = column[Array[Byte]]("key")
  def password = column[Array[Byte]]("password")
  def createdAt = column[ZonedDateTime]("created_at")

  def * =
    (userId, kind, certificate, key, password, createdAt) <>
      ((models.UserCertificate.apply _).tupled, models.UserCertificate.unapply)
}

object UserCertificate extends PersistModel[models.UserCertificate, UserCertificateTable] {
  val table = TableQuery[UserCertificateTable]

  def createRootAndClient(
    userId: Long,
    rootCertificate: Array[Byte],
    rootKey: Array[Byte],
    rootPassword: Array[Byte]
  )(implicit ec: ExecutionContext): Future[ValidationResultUnit] = {
    val timeAt = ZonedDateTime.now()
    val certs = Seq(
      models.UserCertificate(
        userId = userId,
        kind = models.UserCertificateKind.Root,
        certificate = rootCertificate,
        key = rootKey,
        password = rootPassword,
        createdAt = timeAt
      )
    )
    runInTransaction(createDBIO(certs).map(_ => ().success))
  }
}
