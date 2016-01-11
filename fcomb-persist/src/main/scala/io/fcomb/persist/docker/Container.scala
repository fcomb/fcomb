package io.fcomb.persist.docker

import akka.stream.Materializer
import io.fcomb.Db.db
import io.fcomb.RichPostgresDriver.api._
import io.fcomb.models.docker.{ContainerState, Container ⇒ MContainer}
import io.fcomb.request
import io.fcomb.response
import io.fcomb.persist._
import io.fcomb.validations._
import io.fcomb.utils.{StringUtils, Random}
import scala.concurrent.{ExecutionContext, Future}
import java.time.ZonedDateTime

class ContainerTable(tag: Tag) extends Table[MContainer](tag, "containers") with PersistTableWithAutoLongPk {
  def state = column[ContainerState.ContainerState]("state")
  def userId = column[Long]("user_id")
  def applicationId = column[Long]("application_id")
  def nodeId = column[Option[Long]]("node_id")
  def name = column[String]("name")
  def number = column[Int]("number")
  def dockerId = column[Option[String]]("docker_id")
  def createdAt = column[ZonedDateTime]("created_at")
  def updatedAt = column[ZonedDateTime]("updated_at")
  def terminatedAt = column[Option[ZonedDateTime]]("terminated_at")

  def * =
    (id, state, userId, applicationId, nodeId, name,
      number, dockerId, createdAt, updatedAt, terminatedAt) <>
      ((MContainer.apply _).tupled, MContainer.unapply)
}

object Container extends PersistModelWithAutoLongPk[MContainer, ContainerTable] {
  val table = TableQuery[ContainerTable]

  def create(
    userId:        Long,
    applicationId: Long,
    name:          String,
    number:        Int
  )(
    implicit
    ec: ExecutionContext
  ): Future[ValidationModel] = {
    val timeNow = ZonedDateTime.now
    create(MContainer(
      state = ContainerState.Initializing,
      userId = userId,
      applicationId = applicationId,
      nodeId = None,
      name = s"$name-$number",
      number = number,
      createdAt = timeNow,
      updatedAt = timeNow
    ))
  }

  def batchCreate(
    userId:        Long,
    applicationId: Long,
    name:          String,
    numbers:       List[Int]
  )(
    implicit
    ec: ExecutionContext
  ): Future[Seq[MContainer]] = {
    val timeNow = ZonedDateTime.now
    val containers = numbers.map { number ⇒
      MContainer(
        state = ContainerState.Initializing,
        userId = userId,
        applicationId = applicationId,
        nodeId = None,
        name = s"$name-$number",
        number = number,
        createdAt = timeNow,
        updatedAt = timeNow
      )
    }
    db.run(createDBIO(containers))
  }

  def batchPartialUpdate(containers: Seq[MContainer])(
    implicit
    ec: ExecutionContext
  ): Future[Seq[MContainer]] = {
    def f(container: MContainer) =
      table.filter(_.id === container.getId)
        .map(t ⇒ (t.state, t.nodeId, t.dockerId))
        .update((container.state, container.nodeId, container.dockerId))

    val timeNow = ZonedDateTime.now
    val updated = containers.map(_.copy(updatedAt = timeNow))
    db.run {
      DBIO.seq(updated.map(f): _*).map(_ ⇒ updated)
    }
  }

  def updateState(
    ids:   List[Long],
    state: ContainerState.ContainerState
  ) = db.run {
    table.filter(_.id inSetBind ids)
      .map(_.state)
      .update(state)
  }

  private val findAllByApplicationIdCompiled = Compiled { applicationId: Rep[Long] ⇒
    table.filter(_.applicationId === applicationId)
  }

  def findAllByApplicationId(applicationId: Long) = db.run {
    findAllByApplicationIdCompiled(applicationId).result
  }

  private val findAllByNodeIdCompiled = Compiled { nodeId: Rep[Long] ⇒
    table.filter(_.nodeId === nodeId)
  }

  def findAllByNodeId(nodeId: Long) = db.run {
    findAllByNodeIdCompiled(nodeId).result
  }

  def updateState(
    id:        Long,
    state:     ContainerState.ContainerState,
    updatedAt: ZonedDateTime
  ) = db.run {
    table.filter(_.id === id)
      .map(t ⇒ (t.state, t.updatedAt))
      .update((state, updatedAt))
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
