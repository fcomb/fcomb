package io.fcomb.tests.fixtures

import akka.stream._
import akka.stream.scaladsl._
import akka.util.ByteString
import cats.syntax.eq._
import io.fcomb.utils.Config
import io.fcomb.{models ⇒ M}
import io.fcomb.models.errors.{FailureResponse, DtCemException}
import io.fcomb.{persist ⇒ P}
import java.io.File
import java.time.ZonedDateTime
import java.util.UUID
import org.apache.commons.codec.digest.DigestUtils
import org.slf4j.LoggerFactory
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}
import cats.data.Validated

object Fixtures {
  lazy val logger = LoggerFactory.getLogger(getClass)

  def await[T](fut: Future[T])(implicit timeout: Duration = 10.seconds): T =
    Await.result(fut, timeout)

  object User {
    val email = "test@fcomb.io"
    val username = "test"
    val password = "password"
    val fullName = Some("Test Test")

    def create(
      email:    String         = email,
      username: String         = username,
      password: String         = password,
      fullName: Option[String] = fullName
    ) =
      P.User.create(
        email = email,
        username = username,
        password = password,
        fullName = fullName
      ).map(getSuccess)
  }

  object DockerDistributionImage {
    def create(userId: Long, imageName: String)(
      implicit
      ec:  ExecutionContext,
      mat: Materializer
    ) =
      (for {
        Validated.Valid(imageId) ← P.docker.distribution.Image.findIdOrCreateByName(imageName, userId)
      } yield imageId)
  }

  object DockerDistributionImageBlob {
    def create(
      userId:    Long,
      imageName: String
    )(
      implicit
      ec:  ExecutionContext,
      mat: Materializer
    ) =
      (for {
        imageId ← DockerDistributionImage.create(userId, imageName)
        id = UUID.randomUUID()
        blob = M.docker.distribution.ImageBlob(
          id = Some(id),
          state = M.docker.distribution.ImageBlobState.Created,
          imageId = imageId,
          sha256Digest = None,
          contentType = "application/octet-stream",
          length = 0L,
          createdAt = ZonedDateTime.now(),
          uploadedAt = None
        )
        Validated.Valid(res) ← P.docker.distribution.ImageBlob.create(blob)
      } yield res)

    def createAs(
      userId:    Long,
      imageName: String,
      bs:        ByteString,
      state:     M.docker.distribution.ImageBlobState.ImageBlobState
    )(
      implicit
      ec:  ExecutionContext,
      mat: Materializer
    ) =
      (for {
        imageId ← DockerDistributionImage.create(userId, imageName)
        id = UUID.randomUUID()
        digest = DigestUtils.sha256Hex(bs.toArray)
        blob = M.docker.distribution.ImageBlob(
          id = Some(id),
          state = state,
          imageId = imageId,
          sha256Digest = Some(digest),
          length = bs.length.toLong,
          contentType = "application/octet-stream",
          createdAt = ZonedDateTime.now(),
          uploadedAt = None
        )
        Validated.Valid(res) ← P.docker.distribution.ImageBlob.create(blob)
        filename = if (state === M.docker.distribution.ImageBlobState.Uploaded) digest
        else id.toString
        file = new File(s"${Config.docker.distribution.imageStorage}/$filename")
        _ ← Source.single(bs).runWith(FileIO.toFile(file))
      } yield res)
  }

  private def getSuccess[T](res: Validated[_, T]) =
    res match {
      case Validated.Valid(res) ⇒ res
      case Validated.Invalid(e) ⇒
        val errors = FailureResponse.fromExceptions(e.asInstanceOf[List[DtCemException]]).toString
        logger.error(errors)
        throw new IllegalStateException(errors)
    }
}
