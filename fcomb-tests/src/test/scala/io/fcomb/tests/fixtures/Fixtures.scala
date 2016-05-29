package io.fcomb.tests.fixtures

import akka.stream._
import akka.stream.scaladsl._
import akka.util.ByteString
import cats.data.{Xor, Validated}
import cats.syntax.eq._
import io.circe.generic.auto._
import io.circe.parser.decode
import io.fcomb.Db.db
import io.fcomb.json.docker.distribution.Formats._
import io.fcomb.models.errors.{FailureResponse, DtCemException}
import io.fcomb.utils.Config
import io.fcomb.{models ⇒ M}
import io.fcomb.{persist ⇒ P}
import java.io.File
import java.time.ZonedDateTime
import java.util.UUID
import org.apache.commons.codec.digest.DigestUtils
import org.slf4j.LoggerFactory
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}

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

  object docker {
    object distribution {
      import M.docker.distribution.{ImageBlob ⇒ MImageBlob, ImageManifest ⇒ MImageManifest, _}

      object Image {
        def create(userId: Long, imageName: String)(implicit ec: ExecutionContext) =
          P.docker.distribution.Image.findIdOrCreateByName(imageName, userId)
            .map(getSuccess)
      }

      object ImageBlob {
        def create(
          userId:    Long,
          imageName: String
        )(implicit ec: ExecutionContext) =
          (for {
            imageId ← Image.create(userId, imageName)
            id = UUID.randomUUID()
            blob = MImageBlob(
              id = Some(id),
              state = ImageBlobState.Created,
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
        )(implicit mat: Materializer) = {
          (for {
            imageId ← Image.create(userId, imageName)
            id = UUID.randomUUID()
            digest = DigestUtils.sha256Hex(bs.toArray)
            blob = MImageBlob(
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
            filename = if (state === ImageBlobState.Uploaded) digest
            else id.toString
            file = new File(s"${Config.docker.distribution.imageStorage}/$filename")
            _ ← Source.single(bs).runWith(FileIO.toPath(file.toPath))
          } yield res)
        }
      }

      object ImageManifest {
        def create(
          userId:    Long,
          imageName: String,
          blob:      MImageBlob,
          tags:      List[String]
        )(implicit ec: ExecutionContext): Future[MImageManifest] = {
          val schemaV1JsonBlob = s"""
          {
            "name": "$imageName",
            "tag": "",
            "fsLayers": [
              {"blobSum": "sha256:${blob.sha256Digest.get}"}
            ],
            "architecture": "amd64",
            "history": [
             {"v1Compatibility": "{\"id\":\"1968f6a1578f3116f73716b4fea030ac3c52ed12dc04452cee9d8d3dadbdf53c\",\"created\":\"2016-05-06T14:56:49.723208146Z\",\"container\":\"27c9668b3d5e3a2abeefdb725e1ff739cedda4b19eff906336298608f635b00e\",\"container_config\":{\"hostname\":\"27c9668b3d5e\",\"domainname\":\"\",\"user\":\"\",\"attachStdin\":false,\"attachStdout\":false,\"attachStderr\":false,\"exposedPorts\":[],\"tty\":false,\"openStdin\":false,\"stdinOnce\":false,\"env\":[],\"cmd\":[\"/bin/sh\",\"-c\",\"#(nop) ADD file:614a9122187935fccfa72039b9efa3ddbf371f6b029bb01e2073325f00c80b9f in /\"],\"image\":\"\",\"volumes\":{},\"workingDir\":\"\",\"entrypoint\":[],\"onBuild\":[],\"labels\":{}},\"docker_version\":\"1.9.1\",\"config\":{\"hostname\":\"27c9668b3d5e\",\"domainname\":\"\",\"user\":\"\",\"attachStdin\":false,\"attachStdout\":false,\"attachStderr\":false,\"exposedPorts\":[],\"tty\":false,\"openStdin\":false,\"stdinOnce\":false,\"env\":[],\"cmd\":[],\"image\":\"\",\"volumes\":{},\"workingDir\":\"\",\"entrypoint\":[],\"onBuild\":[],\"labels\":{}},\"architecture\":\"amd64\",\"os\":\"linux\"}"}
            ],
            "schemaVersion": 1
          }
          """
          val schemaV2JsonBlob = s"""
          {
            "schemaVersion": 2,
            "mediaType": "application/vnd.docker.distribution.manifest.v2+json",
            "config": {
              "mediaType": "application/vnd.docker.container.image.v1+json",
              "size": ${blob.length},
              "digest": "sha256:${blob.sha256Digest.get}"
            },
            "layers": [
              {
                "mediaType": "application/vnd.docker.image.rootfs.diff.tar.gzip",
                "size": ${blob.length},
                "digest": "sha256:${blob.sha256Digest.get}"
              }
            ]
          }
          """
          val Xor.Right(manifest) = decode[SchemaV2.Manifest](schemaV2JsonBlob)
          val sha256Digest = DigestUtils.sha256Hex(schemaV2JsonBlob)
          val reference = Reference.Digest(sha256Digest)
          for {
            Some(image) ← P.docker.distribution.Image.findByPk(blob.imageId)
            Validated.Valid(im) ← P.docker.distribution.ImageManifest.upsertSchemaV2(
              image = image,
              manifest = manifest,
              reference = reference,
              configBlob = blob,
              schemaV1JsonBlob = schemaV1JsonBlob,
              schemaV2JsonBlob = schemaV2JsonBlob,
              sha256Digest = sha256Digest
            )
            _ ← db.run(for {
              _ ← P.docker.distribution.ImageManifestTag.upsertTagsDBIO(im.imageId, im.getId, tags)
              _ ← P.docker.distribution.ImageManifest.updateDBIO(im.copy(tags = tags))
            } yield ())
          } yield im
        }
      }
    }
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
