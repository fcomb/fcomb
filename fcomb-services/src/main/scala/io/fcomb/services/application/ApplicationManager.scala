package io.fcomb.services.application

import io.fcomb.models.application.{Application ⇒ MApplication}
import io.fcomb.persist.application.{Application ⇒ PApplication}
import io.fcomb.request.ApplicationRequest
import scala.concurrent.{ExecutionContext, Future}
import scalaz._

object ApplicationManager {
  def create(userId: Long, req: ApplicationRequest)(
    implicit
    ec: ExecutionContext
  ): Future[PApplication.ValidationModel] =
    PApplication.createByRequest(userId, req).andThen {
      case scala.util.Success(Success(res)) ⇒
        ApplicationProcessor.start(res.getId)
    }
}
