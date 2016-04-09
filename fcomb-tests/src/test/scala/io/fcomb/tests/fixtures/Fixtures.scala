package io.fcomb.tests.fixtures

import io.fcomb.{persist ⇒ P}
import io.fcomb.{models ⇒ M}
import io.fcomb.utils.Config
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scalaz.{Success, Failure, Validation}
import akka.stream._
import akka.stream.scaladsl._
import akka.util.ByteString
import org.slf4j.LoggerFactory
import java.time.ZonedDateTime
import java.io.File
import java.util.UUID
import org.apache.commons.codec.digest.DigestUtils

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
        Success(imageId) ← P.docker.distribution.Image.findIdOrCreateByName(imageName, userId)
      } yield imageId)
  }

  object DockerDistributionBlob {
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
        blob = M.docker.distribution.Blob(
          id = Some(id),
          state = M.docker.distribution.BlobState.Created,
          imageId = imageId,
          sha256Digest = None,
          length = 0L,
          createdAt = ZonedDateTime.now(),
          uploadedAt = None
        )
        Success(res) ← P.docker.distribution.Blob.create(blob)
      } yield res)

    def createAs(
      userId:    Long,
      imageName: String,
      bs:        ByteString,
      state:     M.docker.distribution.BlobState.BlobState
    )(
      implicit
      ec:  ExecutionContext,
      mat: Materializer
    ) =
      (for {
        imageId ← DockerDistributionImage.create(userId, imageName)
        id = UUID.randomUUID()
        blob = M.docker.distribution.Blob(
          id = Some(id),
          state = state,
          imageId = imageId,
          sha256Digest = Some(DigestUtils.sha256Hex(bs.toArray)),
          length = bs.length.toLong,
          createdAt = ZonedDateTime.now(),
          uploadedAt = None
        )
        Success(res) ← P.docker.distribution.Blob.create(blob)
        file = new File(s"${Config.docker.distribution.imageStorage}/$id")
        _ ← Source.single(bs).runWith(FileIO.toFile(file))
      } yield res)
  }

  private def getSuccess[T](res: Validation[_, T]) =
    res match {
      case Success(res) ⇒ res
      case Failure(e) ⇒
        logger.error(e.toString)
        throw new IllegalStateException(e.toString)
    }
}
