package io.fcomb.persist.node

import akka.stream.Materializer
import io.fcomb.Db.db
import io.fcomb.RichPostgresDriver.api._
import io.fcomb.models.node.{Node ⇒ MNode, NodeState}
import io.fcomb.request
import io.fcomb.persist._
import io.fcomb.validations._
import java.time.LocalDateTime
import scala.concurrent.{ExecutionContext, Future}
import java.time.ZonedDateTime
import java.util.UUID

class NodeTable(tag: Tag) extends Table[MNode](tag, "nodes") with PersistTableWithAutoLongPk {
  def state = column[NodeState.NodeState]("state")
  def token = column[String]("token")
  def rootCertificateId = column[Long]("root_certificate_id")
  def signedCertificate = column[Array[Byte]]("signed_certificate")
  def publicKeySha256 = column[String]("public_key_sha256")
  def createdAt = column[ZonedDateTime]("created_at")
  def updatedAt = column[ZonedDateTime]("updated_at")

  def * =
    (id, state, token, rootCertificateId, signedCertificate,
      publicKeySha256, createdAt, updatedAt) <>
      ((MNode.apply _).tupled, MNode.unapply)
}

object Node extends PersistModelWithAutoLongPk[MNode, NodeTable] {
  val table = TableQuery[NodeTable]

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
