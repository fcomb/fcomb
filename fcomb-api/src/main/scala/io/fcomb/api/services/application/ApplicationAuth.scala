package io.fcomb.api.services.application

import io.fcomb.api.services._
import io.fcomb.persist.application.{Application ⇒ PApplication}
import scala.concurrent.ExecutionContext

trait ApplicationAuth {
  this: Service ⇒

  protected def checkOwner(id: Long)(f: ⇒ ServiceResult)(
    implicit
    ctx: ServiceContext,
    ec:  ExecutionContext
  ) =
    authorizeUser { user ⇒
      complete(
        PApplication
          .isOwner(user.getId, id)
          .map {
            case true  ⇒ f
            case false ⇒ completeNotFound()
          }
      )
    }
}
