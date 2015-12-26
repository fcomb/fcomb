package io.fcomb.persist.docker

import akka.stream.Materializer
import io.fcomb.Db.db
import io.fcomb.RichPostgresDriver.api._
import io.fcomb.models.docker.{DockerContainer ⇒ MDockerContainer, ContainerState}
import io.fcomb.request
import io.fcomb.response
import io.fcomb.persist._
import io.fcomb.validations._
import io.fcomb.utils.{StringUtils, Random}
import scala.concurrent.{ExecutionContext, Future}
import java.time.ZonedDateTime

class ContainerTable(tag: Tag) extends Table[MDockerContainer](tag, "containers") with PersistTableWithAutoLongPk {
  def state = column[ContainerState.ContainerState]("state")
  def userId = column[Long]("user_id")
  def applicationId = column[Long]("application_id")
  def nodeId = column[Long]("node_id")
  def name = column[String]("name")
  def createdAt = column[ZonedDateTime]("created_at")
  def terminatedAt = column[Option[ZonedDateTime]]("terminated_at")

  def * =
    (id, state, userId, applicationId, nodeId, name,
      createdAt, terminatedAt) <>
      ((MDockerContainer.apply _).tupled, MDockerContainer.unapply)
}

object Container extends PersistModelWithAutoLongPk[MDockerContainer, ContainerTable] {
  val table = TableQuery[ContainerTable]

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
