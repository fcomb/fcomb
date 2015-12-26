package io.fcomb.persist.node

import akka.stream.Materializer
import io.fcomb.Db.db
import io.fcomb.RichPostgresDriver.api._
import io.fcomb.models.node.{Node ⇒ MNode, NodeState}
import io.fcomb.request
import io.fcomb.response
import io.fcomb.persist._
import io.fcomb.validations._
import io.fcomb.utils.{StringUtils, Random}
import scala.concurrent.{ExecutionContext, Future}
import org.apache.commons.codec.digest.DigestUtils
import java.time.ZonedDateTime
import java.security.PublicKey
import java.util.{Base64, UUID}
import java.io.StringWriter

class NodeTable(tag: Tag) extends Table[MNode](tag, "nodes") with PersistTableWithAutoLongPk {
  def userId = column[Long]("user_id")
  def state = column[NodeState.NodeState]("state")
  def token = column[String]("token")
  def rootCertificateId = column[Long]("root_certificate_id")
  def signedCertificate = column[Array[Byte]]("signed_certificate")
  def publicKeyHash = column[String]("public_key_hash")
  def createdAt = column[ZonedDateTime]("created_at")
  def updatedAt = column[ZonedDateTime]("updated_at")
  def terminatedAt = column[Option[ZonedDateTime]]("terminated_at")

  def * =
    (id, userId, state, token, rootCertificateId, signedCertificate,
      publicKeyHash, createdAt, updatedAt, terminatedAt) <>
      ((MNode.apply _).tupled, MNode.unapply)
}

object Node extends PersistModelWithAutoLongPk[MNode, NodeTable] {
  val table = TableQuery[NodeTable]

  def getPublicKeyHash(key: PublicKey): String =
    DigestUtils.sha256Hex(key.getEncoded())

  def getNodeIdSequence() = db.run {
    sql"select nextval('nodes_id_seq'::regclass)"
      .as[Long]
      .head
  }

  private def toPemAsCertificate(encoded: Array[Byte]) = {
    val writer = new StringWriter()
    writer.write(s"-----BEGIN CERTIFICATE-----\r\n")
    writer.write(Base64.getMimeEncoder().encodeToString(encoded))
    writer.write(s"\r\n-----END CERTIFICATE-----\r\n")
    writer.toString()
  }

  def create(
    id:                Long,
    userId:            Long,
    rootCertificateId: Long,
    signedCertificate: Array[Byte],
    publicKeyHash:     String
  )(implicit ec: ExecutionContext): Future[ValidationModel] = {
    val timeNow = ZonedDateTime.now()
    super.create(MNode(
      id = Some(id),
      userId = userId,
      state = NodeState.Initializing,
      token = Random.random.alphanumeric.take(128).mkString,
      rootCertificateId = rootCertificateId,
      signedCertificate = signedCertificate,
      publicKeyHash = publicKeyHash,
      createdAt = timeNow,
      updatedAt = timeNow
    ))
  }

  private val findByIdAsAgentCompiled = Compiled { id: Rep[Long] ⇒
    table
      .filter(_.id === id)
      .join(UserCertificate.table).on(_.rootCertificateId === _.id)
      .map {
        case (t, uct) ⇒
          (t.token, t.state, t.signedCertificate, t.createdAt,
            t.updatedAt, uct.certificate)
      }
  }

  def findByIdAndTokenAsAgentResponse(id: Long, token: String)(
    implicit
    ec: ExecutionContext
  ) = db.run {
    findByIdAsAgentCompiled(id).result.headOption.map {
      case Some((nodeToken, state, signedCert, createdAt, updatedAt, rootCert)) ⇒
        if (StringUtils.equalSecure(token, nodeToken))
          Some(response.AgentNodeResponse(
            state = state,
            rootCertificate = toPemAsCertificate(rootCert),
            signedCertificate = toPemAsCertificate(signedCert),
            createdAt = createdAt,
            updatedAt = updatedAt
          ))
        else None
      case None ⇒ None
    }
  }

  private val findByPublicKeyHashCompiled = Compiled { hash: Rep[String] ⇒
    table.filter(_.publicKeyHash === hash).take(1)
  }

  def findByPublicKeyHash(hash: String) = db.run {
    findByPublicKeyHashCompiled(hash).result.headOption
  }

  // private val uniqueTitleCompiled = Compiled {
  //   (id: Rep[Option[Long]], kind: Rep[DictionaryKind.DictionaryKind], title: Rep[String]) ⇒
  //     notCurrentPkFilter(id).filter { q ⇒
  //       q.kind === kind && q.title.toLowerCase === title.toLowerCase
  //     }.exists
  // }

  // import Validations._

  // override def validate(d: models.DictionaryItem)(implicit ec: ExecutionContext): ValidationDBIOResult = {
  //   val plainValidations = validatePlain(
  //     "title" → List(lengthRange(d.title, 1, 255))
  //   )
  //   val dbioValidations = validateDBIO(
  //     "title" → List(unique(uniqueTitleCompiled(d.id, d.kind, d.title)))
  //   )
  //   validate(plainValidations, dbioValidations)
  // }
}
