package io.fcomb.persist

import io.fcomb.Db.db
import io.fcomb.RichPostgresDriver.api._
import io.fcomb.models
import scalaz._, Scalaz._
import scala.concurrent.{ExecutionContext, Future}
import java.time.ZonedDateTime

class UserCertificateTable(tag: Tag) extends Table[models.UserCertificate](tag, "user_certificates")
    with PersistTableWithAutoLongPk {

  def userId = column[Long]("user_id")
  def kind = column[models.CertificateKind.CertificateKind]("kind")
  def certificate = column[Array[Byte]]("certificate")
  def key = column[Array[Byte]]("key")
  def createdAt = column[ZonedDateTime]("created_at")
  def updatedAt = column[ZonedDateTime]("updated_at")

  def * =
    (id, userId, kind, certificate, key, createdAt, updatedAt) <>
      ((models.UserCertificate.apply _).tupled, models.UserCertificate.unapply)
}

object UserCertificate extends PersistModelWithAutoLongPk[models.UserCertificate, UserCertificateTable] {
  val table = TableQuery[UserCertificateTable]

  def createRootAndClient(
    userId:            Long,
    rootCertificate:   Array[Byte],
    rootKey:           Array[Byte],
    clientCertificate: Array[Byte],
    clientKey:         Array[Byte]
  )(implicit ec: ExecutionContext): Future[ValidationModel] = {
    val timeAt = ZonedDateTime.now()
    val rootCert = models.UserCertificate(
      userId = userId,
      kind = models.CertificateKind.Root,
      certificate = rootCertificate,
      key = rootKey,
      createdAt = timeAt,
      updatedAt = timeAt
    )
    val clientCert = models.UserCertificate(
      userId = userId,
      kind = models.CertificateKind.Client,
      certificate = clientCertificate,
      key = clientKey,
      createdAt = timeAt,
      updatedAt = timeAt
    )
    runInTransaction(for {
      root ← createDBIO(rootCert)
      client ← createDBIO(clientCert)
    } yield root.success)
  }

  private val findRootCertByUserIdCompiled = Compiled { userId: Rep[Long] ⇒
    table.filter(_.userId === userId).take(1)
  }

  def findRootCertByUserId(userId: Long) = db.run {
    findRootCertByUserIdCompiled(userId).result.headOption
  }
}
