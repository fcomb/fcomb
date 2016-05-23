package io.fcomb.services.application

import io.fcomb.models.application.{ScaleStrategy, Application ⇒ MApplication}
import io.fcomb.persist.application.{Application ⇒ PApplication}
import io.fcomb.request.ApplicationRequest
import scala.concurrent.{ExecutionContext, Future}
import cats.data.Validated

object ApplicationManager {
  def create(userId: Long, req: ApplicationRequest)(
    implicit
    ec: ExecutionContext
  ): Future[PApplication.ValidationModel] =
    PApplication.createByRequest(userId, req).andThen {
      case scala.util.Success(Validated.Valid(res)) ⇒
        ApplicationProcessor.start(res.getId)
    }

  @inline
  def stop(id: Long)(
    implicit
    ec: ExecutionContext
  ): Future[Unit] =
    ApplicationProcessor.stop(id)

  @inline
  def start(id: Long)(
    implicit
    ec: ExecutionContext
  ): Future[Unit] =
    ApplicationProcessor.start(id)

  @inline
  def restart(id: Long)(
    implicit
    ec: ExecutionContext
  ): Future[Unit] =
    ApplicationProcessor.restart(id)

  @inline
  def terminate(id: Long)(
    implicit
    ec: ExecutionContext
  ): Future[Unit] =
    ApplicationProcessor.terminate(id)

  @inline
  def redeploy(id: Long, scaleStrategy: Option[ScaleStrategy])(
    implicit
    ec: ExecutionContext
  ): Future[Unit] =
    ApplicationProcessor.redeploy(id, scaleStrategy)

  @inline
  def scale(id: Long, numberOfContainers: Int)(
    implicit
    ec: ExecutionContext
  ): Future[Unit] =
    ApplicationProcessor.scale(id, numberOfContainers)
}
