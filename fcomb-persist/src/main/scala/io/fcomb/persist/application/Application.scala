package io.fcomb.persist.application

import akka.stream.Materializer
import io.fcomb.Db.db
import io.fcomb.RichPostgresDriver.api._
import io.fcomb.models.application.{ApplicationState, ScaleStrategy, ScaleStrategyKind, Application ⇒ MApplication, _}
import io.fcomb.request
import io.fcomb.response
import io.fcomb.persist._
import io.fcomb.validations._
import io.fcomb.json.{dockerDeployPortJsonProtocol, networkPortJsonProtocol}
import io.fcomb.utils.{StringUtils, Random}
import scala.concurrent.{ExecutionContext, Future}
import spray.json._, DefaultJsonProtocol._
import java.time.ZonedDateTime

class ApplicationTable(tag: Tag) extends Table[MApplication](tag, "applications") with PersistTableWithAutoLongPk {
  def userId = column[Long]("user_id")
  def state = column[ApplicationState.ApplicationState]("state")
  def name = column[String]("name")
  def createdAt = column[ZonedDateTime]("created_at")
  def updatedAt = column[ZonedDateTime]("updated_at")
  def terminatedAt = column[Option[ZonedDateTime]]("terminated_at")

  // docker image columns
  def diName = column[String]("di_name")
  def diTag = column[Option[String]]("di_tag")
  def diRegistry = column[Option[String]]("di_registry")

  // docker deploy options
  def ddoPorts = column[JsValue]("ddo_ports")
  def ddoIsAutoRestart = column[Boolean]("ddo_is_auto_restart")
  def ddoIsAutoDestroy = column[Boolean]("ddo_is_auto_destroy")
  def ddoIsPrivileged = column[Boolean]("ddo_is_privileged")
  def ddoCommand = column[Option[String]]("ddo_command")
  def ddoEntrypoint = column[Option[String]]("ddo_entrypoint")
  def ddoMemoryLimit = column[Option[Long]]("ddo_memory_limit")
  def ddoCpuShares = column[Option[Long]]("ddo_cpu_shares")

  // scale strategy
  def ssKind = column[ScaleStrategyKind.ScaleStrategyKind]("ss_kind")
  def ssNumberOfContainers = column[Int]("ss_number_of_containers")

  def * =
    (id, userId, state, name, createdAt, updatedAt, terminatedAt,
      // docker image tuple
      (diName, diTag, diRegistry),
      // docker deploy options tuple
      (ddoPorts, ddoIsAutoRestart, ddoIsAutoDestroy, ddoIsPrivileged,
        ddoCommand, ddoEntrypoint, ddoMemoryLimit, ddoCpuShares),
        (ssKind, ssNumberOfContainers)) <>
        ((apply2 _).tupled, unapply2)

  def apply2(
    id:           Option[Long]                                                                                     = None,
    userId:       Long,
    state:        ApplicationState.ApplicationState,
    name:         String,
    createdAt:    ZonedDateTime,
    updatedAt:    ZonedDateTime,
    terminatedAt: Option[ZonedDateTime],
    diTuple:      (String, Option[String], Option[String]),
    ddoTuple:     (JsValue, Boolean, Boolean, Boolean, Option[String], Option[String], Option[Long], Option[Long]),
    ssTuple:      (ScaleStrategyKind.ScaleStrategyKind, Int)
  ) = {
    val image = DockerImage.tupled(diTuple)
    val ports = ddoTuple._1.convertTo[Set[DockerDeployPort]] // TODO: handle serialize errors and case class changes
    val deployOptions = DockerDeployOptions.tupled(
      ddoTuple.copy(_1 = ports)
    )
    val scaleStrategy = ScaleStrategy.tupled(ssTuple)
    MApplication(
      id = id,
      userId = userId,
      state = state,
      name = name,
      image = image,
      deployOptions = deployOptions,
      scaleStrategy = scaleStrategy,
      createdAt = createdAt,
      updatedAt = updatedAt,
      terminatedAt = terminatedAt
    )
  }

  def unapply2(a: MApplication) = {
    val ports = a.deployOptions.ports.toJson
    val deployOptionsTuple =
      DockerDeployOptions.unapply(a.deployOptions)
        .get
        .copy(_1 = ports)
    Some((
      a.id, a.userId, a.state, a.name, a.createdAt, a.updatedAt, a.terminatedAt,
      DockerImage.unapply(a.image).get,
      deployOptionsTuple,
      ScaleStrategy.unapply(a.scaleStrategy).get
    ))
  }
}

object Application extends PersistModelWithAutoLongPk[MApplication, ApplicationTable] {
  val table = TableQuery[ApplicationTable]

  def createByRequest(
    userId: Long,
    req:    request.ApplicationRequest
  )(implicit ec: ExecutionContext): Future[ValidationModel] = {
    val timeNow = ZonedDateTime.now()
    create(MApplication(
      userId = userId,
      state = ApplicationState.Created,
      name = req.name,
      image = req.image,
      deployOptions = req.deployOptions,
      scaleStrategy = req.scaleStrategy,
      createdAt = timeNow,
      updatedAt = timeNow
    ))
  }

  def updateState(
    id:    Long,
    state: ApplicationState.ApplicationState
  ) = db.run {
    table.filter(_.id === id)
      .map(_.state)
      .update(state)
  }

  def updateNumberOfContainers(
    id:                 Long,
    numberOfContainers: Int
  ) = db.run {
    table.filter(_.id === id)
      .map(_.ssNumberOfContainers)
      .update(numberOfContainers)
  }

  private val isOwnerCompiled = Compiled {
    (userId: Rep[Long], id: Rep[Long]) ⇒
      table.filter { q ⇒
        q.id === id && q.userId === userId
      }.exists
  }

  def isOwner(userId: Long, id: Long) = db.run {
    isOwnerCompiled(userId, id).result
  }

  private val findAllByUserIdCompiled = Compiled { userId: Rep[Long] ⇒
    table.filter(_.userId === userId)
  }

  def findAllByUserId(userId: Long) =
    db.run(findAllByUserIdCompiled(userId).result)

  private val findByIdAndUserIdCompiled = Compiled {
    (id: Rep[Long], userId: Rep[Long]) ⇒
      table.filter { q ⇒
        q.id === id && q.userId === userId
      }
  }

  def findByIdAndUserId(id: Long, userId: Long) =
    db.run(findByIdAndUserIdCompiled(id, userId).result.headOption)

  private val uniqueNameCompiled = Compiled {
    (id: Rep[Option[Long]], userId: Rep[Long], name: Rep[String]) ⇒
      notCurrentPkFilter(id).filter { q ⇒
        q.userId === userId && q.name.toLowerCase === name.toLowerCase
      }.exists
  }

  import Validations._

  override def validate(a: MApplication)(
    implicit
    ec: ExecutionContext
  ): ValidationDBIOResult = {
    val plainValidations = validatePlain(
      "name" → List(lengthRange(a.name, 1, 255))
    )
    val dbioValidations = validateDBIO(
      "name" → List(unique(uniqueNameCompiled(a.id, a.userId, a.name)))
    )
    validate(plainValidations, dbioValidations)
  }
}
