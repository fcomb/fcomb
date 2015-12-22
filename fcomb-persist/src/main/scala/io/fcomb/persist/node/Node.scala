package io.fcomb.persist.node

import akka.stream.Materializer
import io.fcomb.Db.db
import io.fcomb.RichPostgresDriver.api._
import io.fcomb.models.node.{Node ⇒ MNode, NodeState}
import io.fcomb.request
import io.fcomb.persist._
import io.fcomb.validations._
import io.fcomb.utils.Random.random
import java.time.LocalDateTime
import scala.concurrent.{ExecutionContext, Future}
import org.apache.commons.codec.digest.DigestUtils
import java.time.ZonedDateTime
import java.security.PublicKey
import java.util.UUID

class NodeTable(tag: Tag) extends Table[MNode](tag, "nodes") with PersistTableWithAutoLongPk {
  def userId = column[Long]("user_id")
  def state = column[NodeState.NodeState]("state")
  def token = column[String]("token")
  def rootCertificateId = column[Long]("root_certificate_id")
  def signedCertificate = column[Array[Byte]]("signed_certificate")
  def publicKeyHash = column[String]("public_key_hash")
  def createdAt = column[ZonedDateTime]("created_at")
  def updatedAt = column[ZonedDateTime]("updated_at")

  def * =
    (id, userId, state, token, rootCertificateId, signedCertificate,
      publicKeyHash, createdAt, updatedAt) <>
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

  def create(
    id: Long,
    userId: Long,
    rootCertificateId: Long,
    signedCertificate: Array[Byte],
    publicKeyHash: String
  )(implicit ec: ExecutionContext): Future[ValidationModel] = {
    val timeNow = ZonedDateTime.now()
    super.create(MNode(
      id = Some(id),
      userId = userId,
      state = NodeState.Initialize,
      token = random.alphanumeric.take(96).mkString,
      rootCertificateId = rootCertificateId,
      signedCertificate = signedCertificate,
      publicKeyHash = publicKeyHash,
      createdAt = timeNow,
      updatedAt = timeNow
    ))
  }

  // def createByRequest(kind: DictionaryKind.DictionaryKind, req: request.DictionaryItemRequest)(
  //   implicit
  //   ec: ExecutionContext
  // ) = {
  //   val timeNow = ZonedDateTime.now
  //   create(models.DictionaryItem(
  //     kind = kind,
  //     title = req.title,
  //     createdAt = timeNow,
  //     updatedAt = timeNow
  //   ))
  // }

  // def updateByRequest(
  //   id:   Long,
  //   kind: DictionaryKind.DictionaryKind,
  //   req:  request.DictionaryItemRequest
  // )(
  //   implicit
  //   ec: ExecutionContext
  // ) =
  //   update(id)(_.copy(
  //     title = req.title,
  //     updatedAt = ZonedDateTime.now
  //   ))

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
