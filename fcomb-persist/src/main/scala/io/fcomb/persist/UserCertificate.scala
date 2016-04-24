package io.fcomb.persist

import io.fcomb.Db.db
import io.fcomb.RichPostgresDriver.api._
import io.fcomb.models
import cats.data.Validated
import scala.concurrent.{ExecutionContext, Future}
import slick.jdbc.TransactionIsolation
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
    runInTransaction(TransactionIsolation.ReadCommitted)(for {
      root ← createDBIO(rootCert)
      client ← createDBIO(clientCert)
    } yield Validated.Valid(root))
  }

  private val findRootCertByUserIdCompiled = Compiled { userId: Rep[Long] ⇒
    table.filter { q ⇒
      q.userId === userId && q.kind === models.CertificateKind.Root
    }.take(1)
  }

  def findRootCertByUserId(userId: Long) = db.run {
    findRootCertByUserIdCompiled(userId).result.headOption
  }

  private val findRootAndClientCertsByUserIdCompiled = Compiled { userId: Rep[Long] ⇒
    table.filter { q ⇒
      q.userId === userId && (
        q.kind === models.CertificateKind.Root ||
        q.kind === models.CertificateKind.Client
      )
    }.take(2)
  }

  def findRootAndClientCertsByUserId(userId: Long)(
    implicit
    ec: ExecutionContext
  ) =
    db.run {
      findRootAndClientCertsByUserIdCompiled(userId).result.map { xs ⇒
        val root = xs.find(_.kind == models.CertificateKind.Root)
        val client = xs.find(_.kind == models.CertificateKind.Client)
        (root, client) match {
          case (Some(rootCert), Some(clientCert)) ⇒
            Some((rootCert, clientCert))
          case _ ⇒ None
        }
      }
    }
}
