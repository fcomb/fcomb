package io.fcomb.persist.comb

import io.fcomb.Db._
import io.fcomb.RichPostgresDriver.api._
import io.fcomb.models
import io.fcomb.models.comb
import io.fcomb.persist._
import io.fcomb.validations._
import java.time.LocalDateTime
import java.util.UUID
import scala.concurrent.{ ExecutionContext, Future }
import scalaz._
import scalaz.Scalaz._

class CombMethodTable(tag: Tag) extends Table[comb.CombMethod](tag, "comb_methods") with PersistTableWithUuidPk {
  def combId = column[UUID]("comb_id")
  def kind = column[comb.MethodKind.MethodKind]("kind")
  def uri = column[String]("uri")
  def endpoint = column[String]("endpoint")
  def createdAt = column[LocalDateTime]("created_at")
  def updatedAt = column[LocalDateTime]("updated_at")

  def * =
    (id, combId, kind, uri, endpoint, createdAt, updatedAt) <>
      ((comb.CombMethod.apply _).tupled, comb.CombMethod.unapply)
}

object CombMethod extends PersistModelWithUuid[comb.CombMethod, CombMethodTable] {
  val table = TableQuery[CombMethodTable]

  def create(
    combId:   UUID,
    kind:     comb.MethodKind.MethodKind,
    uri:      String,
    endpoint: String
  )(implicit ec: ExecutionContext): Future[ValidationModel] = {
    val timeAt = LocalDateTime.now()
    super.create(comb.CombMethod(
      id = UUID.randomUUID(),
      combId = combId,
      kind = kind,
      uri = uri,
      endpoint = endpoint,
      createdAt = timeAt,
      updatedAt = timeAt
    ))
  }

  // def updateByRequest(id: UUID)(
  //   name: String,
  //   slug: Option[String]
  // )(implicit ec: ExecutionContext): Future[ValidationModel] =
  //   update(id) { comb =>
  //     comb.copy(
  //       name = name,
  //       slug = makeSlug(comb.id, slug),
  //       updatedAt = LocalDateTime.now()
  //     )
  //   }

  private val unqiueUriCompiled = Compiled {
    (combId: Rep[UUID], uri: Rep[String]) =>
      table.filter { f =>
        f.combId === combId && f.uri === uri
      }.exists
  }

  import Validations._

  override def validate(m: comb.CombMethod)(implicit ec: ExecutionContext): ValidationDBIOResult = {
    val plainValidations = validatePlain(
      "uri" -> List(lengthRange(m.uri, 1, 2048)), // TODO: validate URI
      "endpoint" -> List(lengthRange(m.endpoint, 1, 2048)) // TODO: validate URL + passed params
    )
    val dbioValidations = validateDBIO(
      "uri" -> List(unique(unqiueUriCompiled(m.combId, m.uri)))
    )
    validate(plainValidations, dbioValidations)
  }
}
