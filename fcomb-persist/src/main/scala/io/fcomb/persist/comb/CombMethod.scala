package io.fcomb.persist.comb

import io.fcomb.Db._
import io.fcomb.RichPostgresDriver.api._
import io.fcomb.models
import io.fcomb.models.comb
import io.fcomb.persist._
import io.fcomb.trie.RouteTrie
import io.fcomb.validations._
import java.net.URL
import java.time.LocalDateTime
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}
import scalaz._
import scalaz.Scalaz._

class CombMethodTable(tag: Tag) extends Table[comb.CombMethod](tag, "comb_methods")
  with PersistTableWithAutoLongPk {
  def combId = column[Long]("comb_id")
  def kind = column[comb.MethodKind.MethodKind]("kind")
  def uri = column[String]("uri")
  def endpoint = column[String]("endpoint")
  def createdAt = column[LocalDateTime]("created_at")
  def updatedAt = column[LocalDateTime]("updated_at")

  def * =
    (id, combId, kind, uri, endpoint, createdAt, updatedAt) <>
      ((comb.CombMethod.apply _).tupled, comb.CombMethod.unapply)
}

object CombMethod extends PersistModelWithAutoLongPk[comb.CombMethod, CombMethodTable] {
  val table = TableQuery[CombMethodTable]

  def create(
    combId: Long,
    kind: comb.MethodKind.MethodKind,
    uri: String,
    endpoint: String
  )(implicit ec: ExecutionContext): Future[ValidationModel] = {
    val timeAt = LocalDateTime.now()
    create(comb.CombMethod(
      combId = combId,
      kind = kind,
      uri = uri,
      endpoint = endpoint,
      createdAt = timeAt,
      updatedAt = timeAt
    ))
  }

  val findAllByCombIdCompiled = Compiled { combId: Rep[Long] =>
    table.filter(_.combId === combId)
  }

  def findAllByCombId(combId: Long) =
    db.run(findAllByCombIdCompiled(combId).result)

  def destroyByCombId(combId: Long) =
    db.run(table.filter(_.combId === combId).delete)

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

  private val uniqueUriCompiled = Compiled {
    (combId: Rep[Long], uri: Rep[String]) =>
      table.filter { f =>
        f.combId === combId && f.uri === uri
      }.exists
  }

  import Validations._

  private def validateUri(uri: String): PlainValidation =
    RouteTrie.validateUri(uri) match {
      case Right(_) => ().successNel
      case Left(e) => e.failureNel
    }

  private def validateEndpoint(uri: String, endpoint: String): PlainValidation = {
    Try(new URL(endpoint)) match {
      case Success(url) => RouteTrie.validateUri(uri) match {
        case Right(uriParameters) =>
          val unknownParameters = comb.CombMethodUtils.endpointParams(url)
            .filterNot(_ == "*")
            .diff(uriParameters.toList)
          if (unknownParameters.isEmpty) ().successNel
          else s"parameters not from URI: ${unknownParameters.mkString(", ")}".failureNel
        case Left(_) => "invalid uri".failureNel
      }
      case Failure(e) => e.getMessage.failureNel
    }
  }

  override def validate(m: comb.CombMethod)(implicit ec: ExecutionContext): ValidationDBIOResult = {
    val plainValidations = validatePlain(
      "uri" -> List(
        lengthRange(m.uri, 1, 2048),
        validateUri(m.uri)
      ),
      "endpoint" -> List(
        lengthRange(m.endpoint, 1, 2048),
        validateEndpoint(m.uri, m.endpoint)
      )
    )
    val dbioValidations = validateDBIO(
      "uri" -> List(unique(uniqueUriCompiled(m.combId, m.uri)))
    )
    validate(plainValidations, dbioValidations)
  }
}
