package io.fcomb.tests.fixtures.docker.distribution

import java.util.UUID
import java.time.ZonedDateTime
import akka.util.ByteString
import cats.data.Validated
import io.fcomb.models.docker.distribution.{ImageBlob, ImageBlobState}
import io.fcomb.persist.docker.distribution.ImageBlobsRepo
import io.fcomb.docker.distribution.utils.BlobFile
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import org.apache.commons.codec.digest.DigestUtils
import akka.stream.scaladsl.{Source, FileIO}
import akka.stream.Materializer

object ImageBlobsRepoFixture {
  def create(userId: Long, imageName: String): Future[ImageBlob] =
    (for {
      imageId ← ImagesRepoFixture.create(userId, imageName)
      id = UUID.randomUUID()
      blob = ImageBlob(
        id = Some(id),
        state = ImageBlobState.Created,
        imageId = imageId,
        sha256Digest = None,
        contentType = "application/octet-stream",
        length = 0L,
        createdAt = ZonedDateTime.now(),
        uploadedAt = None
      )
      Validated.Valid(res) ← ImageBlobsRepo.create(blob)
    } yield res)

  def createAs(
    userId:    Long,
    imageName: String,
    bs:        ByteString,
    state:     ImageBlobState,
    digestOpt: Option[String] = None
  )(implicit mat: Materializer): Future[ImageBlob] = {
    for {
      imageId ← ImagesRepoFixture.create(userId, imageName)
      id = UUID.randomUUID()
      digest = digestOpt.getOrElse(DigestUtils.sha256Hex(bs.toArray))
      blob = ImageBlob(
        id = Some(id),
        state = state,
        imageId = imageId,
        sha256Digest = Some(digest),
        length = bs.length.toLong,
        contentType = "application/octet-stream",
        createdAt = ZonedDateTime.now(),
        uploadedAt = None
      )
      Validated.Valid(im) ← ImageBlobsRepo.create(blob)
      file = BlobFile.getFile(blob)
      _ = file.getParentFile.mkdirs()
      _ ← Source.single(bs).runWith(FileIO.toPath(file.toPath))
    } yield im
  }
}
