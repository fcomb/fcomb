package io.fcomb.tests.fixtures.docker.distribution

import cats.data.Validated
import io.fcomb.persist.docker.distribution.ImagesRepo
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

object ImagesRepoFixture {
  def create(userId: Long, imageName: String): Future[Long] =
    for {
      Validated.Valid(imageId) ← ImagesRepo.findIdOrCreateByName(imageName, userId)
    } yield imageId
}
