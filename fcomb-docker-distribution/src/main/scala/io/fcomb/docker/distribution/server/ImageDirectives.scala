package io.fcomb.docker.distribution.server

import akka.http.scaladsl.server._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.model._
import io.fcomb.models.User
import io.fcomb.models.docker.distribution.Image
import io.fcomb.models.errors.docker.distribution._
import io.fcomb.persist.docker.distribution.ImagesRepo
import de.heikoseeberger.akkahttpcirce.CirceSupport._
import io.circe.generic.auto._

trait ImageDirectives {
  def imageByNameWithAcl(imageName: String, user: User): Directive1[Image] = {
    extractExecutionContext.flatMap { implicit ec ⇒
      onSuccess(ImagesRepo.findByImageAndUserId(imageName, user.getId)).flatMap {
        case Some(user) ⇒ provide(user)
        case None ⇒
          complete(
            StatusCodes.NotFound,
            DistributionErrorResponse.from(DistributionError.NameUnknown())
          )
      }
    }
  }
}

object ImageDirectives extends ImageDirectives
