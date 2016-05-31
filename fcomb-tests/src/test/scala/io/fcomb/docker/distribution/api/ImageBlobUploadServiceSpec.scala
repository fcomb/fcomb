package io.fcomb.docker.distribution.server.api

import akka.actor.PoisonPill
import akka.http.scaladsl._, model._
import akka.http.scaladsl.model.ContentTypes.`application/octet-stream`
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.model.headers._
import akka.http.scaladsl.testkit.ScalatestRouteTest
import akka.util.ByteString
import de.heikoseeberger.akkahttpcirce.CirceSupport._
import io.circe.generic.auto._
import io.fcomb.docker.distribution.server.api.headers._
import io.fcomb.docker.distribution.server.services.ImageBlobPushProcessor
import io.fcomb.docker.distribution.server.utils.BlobFile
import io.fcomb.json.docker.distribution.Formats._
import io.fcomb.models.docker.distribution._
import io.fcomb.models.errors.docker.distribution._
import io.fcomb.persist.docker.distribution.{Image ⇒ PImage, ImageBlob ⇒ PImageBlob}
import io.fcomb.tests._
import io.fcomb.tests.fixtures.Fixtures
import io.fcomb.utils.StringUtils
import java.io.FileInputStream
import java.security.MessageDigest
import java.util.UUID
import org.apache.commons.codec.digest.DigestUtils
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{Matchers, WordSpec}
import scala.concurrent.Await
import scala.concurrent.duration._

class ImageBlobUploadServiceSpec extends WordSpec with Matchers with ScalatestRouteTest with SpecHelpers with ScalaFutures with PersistSpec with ActorClusterSpec {
  val route = Routes()
  val imageName = "library/test-image_2016"
  val bs = ByteString(getFixture("docker/distribution/blob"))
  val bsDigest = {
    val md = MessageDigest.getInstance("SHA-256")
    md.update(bs.toArray)
    StringUtils.hexify(md.digest)
  }
  // val digest = "300f96719cd9297b942f67578f7e7fe0a4472f9c68c30aff78db728316279e6f"
  val credentials = BasicHttpCredentials(Fixtures.User.username, Fixtures.User.password)
  val clusterRef = ImageBlobPushProcessor.startRegion(30.seconds)

  override def afterAll(): Unit = {
    super.afterAll()
    clusterRef ! PoisonPill
  }

  "The image blob upload service" should {
    "return a uuid for POST request to the start upload path" in {
      Fixtures.await(Fixtures.User.create())

      Post(s"/v2/$imageName/blobs/uploads/") ~> addCredentials(credentials) ~> route ~> check {
        status shouldEqual StatusCodes.Accepted
        responseEntity shouldEqual HttpEntity.Empty
        val uuid = header[`Docker-Upload-Uuid`].map(h ⇒ UUID.fromString(h.value)).get
        header[Location].get shouldEqual Location(s"/v2/$imageName/blobs/uploads/$uuid")
        header[RangeCustom].get shouldEqual RangeCustom(0L, 0L)
      }
    }

    "return successful response for POST request to initiate monolithic blob upload path" in {
      Fixtures.await(Fixtures.User.create())

      Post(
        s"/v2/$imageName/blobs/uploads/?digest=sha256:$bsDigest",
        HttpEntity(`application/octet-stream`, bs)
      ) ~> addCredentials(credentials) ~> route ~> check {
          status shouldEqual StatusCodes.Created
          responseEntity shouldEqual HttpEntity.Empty
          header[Location].get shouldEqual Location(s"/v2/$imageName/blobs/sha256:$bsDigest")
          header[`Docker-Content-Digest`].get shouldEqual `Docker-Content-Digest`("sha256", bsDigest)
          val uuid = header[`Docker-Upload-Uuid`].map(h ⇒ UUID.fromString(h.value)).get

          val blob = Await.result(PImageBlob.findByPk(uuid), 5.seconds).get
          blob.length shouldEqual bs.length
          blob.state shouldEqual ImageBlobState.Uploaded
          blob.sha256Digest shouldEqual Some(bsDigest)

          val file = BlobFile.getBlobFilePath(blob.sha256Digest.get)
          file.length shouldEqual bs.length
          val fis = new FileInputStream(file)
          val fileDigest = DigestUtils.sha256Hex(fis)
          fileDigest shouldEqual bsDigest
        }
    }

    "return failed response for POST request to initiate monolithic blob upload path" in {
      Fixtures.await(Fixtures.User.create())

      Post(
        s"/v2/$imageName/blobs/uploads/?digest=sha256:333f96719cd9297b942f67578f7e7fe0a4472f9c68c30aff78db728316279e6f",
        HttpEntity(`application/octet-stream`, bs)
      ) ~> addCredentials(credentials) ~> route ~> check {
          status shouldEqual StatusCodes.BadRequest
          val resp = responseAs[DistributionErrorResponse]
          resp shouldEqual DistributionErrorResponse(Seq(DistributionError.DigestInvalid()))
        }
    }

    "return successful response for PUT request to mount blob upload path" in {
      val user = Fixtures.await(for {
        user ← Fixtures.User.create()
        blob ← Fixtures.docker.distribution.ImageBlob.createAs(
          user.getId, imageName, bs, ImageBlobState.Uploaded
        )
      } yield user)
      val newImageName = "newimage/name"

      Post(
        s"/v2/$newImageName/blobs/uploads/?mount=sha256:$bsDigest&from=$imageName",
        HttpEntity(`application/octet-stream`, bs)
      ) ~> addCredentials(credentials) ~> route ~> check {
          status shouldEqual StatusCodes.Created
          responseEntity shouldEqual HttpEntity.Empty
          header[Location].get shouldEqual Location(s"/v2/$newImageName/blobs/sha256:$bsDigest")
          header[`Docker-Content-Digest`].get shouldEqual `Docker-Content-Digest`("sha256", bsDigest)

          val newBlob = Await.result({
            for {
              Some(image) ← PImage.findByImageAndUserId(newImageName, user.getId)
              Some(blob) ← PImageBlob.findByImageIdAndDigest(image.getId, bsDigest)
            } yield blob
          }, 5.seconds)
          newBlob.length shouldEqual bs.length
          newBlob.state shouldEqual ImageBlobState.Uploaded
          newBlob.sha256Digest shouldEqual Some(bsDigest)
        }
    }

    "return successful response for PUT request to monolithic blob upload path" in {
      val blob = Fixtures.await(for {
        user ← Fixtures.User.create()
        blob ← Fixtures.docker.distribution.ImageBlob.create(user.getId, imageName)
      } yield blob)

      Put(
        s"/v2/$imageName/blobs/uploads/${blob.getId}?digest=sha256:$bsDigest",
        HttpEntity(`application/octet-stream`, bs)
      ) ~> addCredentials(credentials) ~> route ~> check {
          status shouldEqual StatusCodes.Created
          responseEntity shouldEqual HttpEntity.Empty
          header[Location].get shouldEqual Location(s"/v2/$imageName/blobs/sha256:$bsDigest")
          header[`Docker-Content-Digest`].get shouldEqual `Docker-Content-Digest`("sha256", bsDigest)
          val uuid = header[`Docker-Upload-Uuid`].map(h ⇒ UUID.fromString(h.value)).get

          val blob = Await.result(PImageBlob.findByPk(uuid), 5.seconds).get
          blob.length shouldEqual bs.length
          blob.state shouldEqual ImageBlobState.Uploaded
          blob.sha256Digest shouldEqual Some(bsDigest)

          val file = BlobFile.getUploadFilePath(blob.getId)
          file.length shouldEqual bs.length
          val fis = new FileInputStream(file)
          val fileDigest = DigestUtils.sha256Hex(fis)
          fileDigest shouldEqual bsDigest
        }
    }

    "return successful response for PATCH requests to blob upload path" in {
      val blob = Fixtures.await(for {
        user ← Fixtures.User.create()
        blob ← Fixtures.docker.distribution.ImageBlob.create(user.getId, imageName)
      } yield blob)

      val blobPart1 = bs.take(bs.length / 2)
      val blobPart1Digest = DigestUtils.sha256Hex(blobPart1.toArray)

      Patch(
        s"/v2/$imageName/blobs/uploads/${blob.getId}",
        HttpEntity(`application/octet-stream`, blobPart1)
      ) ~> `Content-Range`(ContentRange(0L, blobPart1.length - 1L)) ~>
        addCredentials(credentials) ~> route ~> check {
          status shouldEqual StatusCodes.Accepted
          responseEntity shouldEqual HttpEntity.Empty
          header[Location].get shouldEqual Location(s"/v2/$imageName/blobs/${blob.getId}")
          header[`Docker-Upload-Uuid`].get shouldEqual `Docker-Upload-Uuid`(blob.getId)
          header[RangeCustom].get shouldEqual RangeCustom(0L, blobPart1.length - 1L)

          val updatedBlob = Await.result(PImageBlob.findByPk(blob.getId), 5.seconds).get
          updatedBlob.length shouldEqual blobPart1.length
          updatedBlob.state shouldEqual ImageBlobState.Uploading
          updatedBlob.sha256Digest shouldEqual Some(blobPart1Digest)

          val file = BlobFile.getUploadFilePath(blob.getId)
          file.length shouldEqual blobPart1.length
          val fis = new FileInputStream(file)
          val fileDigest = DigestUtils.sha256Hex(fis)
          fileDigest shouldEqual blobPart1Digest
        }

      val blobPart2 = bs.drop(blobPart1.length)

      Patch(
        s"/v2/$imageName/blobs/uploads/${blob.getId}",
        HttpEntity(`application/octet-stream`, blobPart2)
      ) ~> `Content-Range`(ContentRange(blobPart1.length.toLong, blobPart2.length - 1L)) ~>
        addCredentials(credentials) ~> route ~> check {
          status shouldEqual StatusCodes.Accepted
          responseEntity shouldEqual HttpEntity.Empty
          header[Location].get shouldEqual Location(s"/v2/$imageName/blobs/${blob.getId}")
          header[`Docker-Upload-Uuid`].get shouldEqual `Docker-Upload-Uuid`(blob.getId)
          header[RangeCustom].get shouldEqual RangeCustom(0L, bs.length - 1L)

          val updatedBlob = Await.result(PImageBlob.findByPk(blob.getId), 5.seconds).get
          updatedBlob.length shouldEqual bs.length
          updatedBlob.state shouldEqual ImageBlobState.Uploading
          updatedBlob.sha256Digest shouldEqual Some(bsDigest)

          val file = BlobFile.getUploadFilePath(blob.getId)
          file.length shouldEqual bs.length
          val fis = new FileInputStream(file)
          val fileDigest = DigestUtils.sha256Hex(fis)
          fileDigest shouldEqual bsDigest
        }
    }

    // TODO
    // "return successful response for first PATCH request to blob upload path" in {
    //   val blob = Fixtures.await(for {
    //     user ← Fixtures.User.create()
    //     blob ← Fixtures.DockerDistributionBlob.create(user.getId, imageName)
    //   } yield blob)
    //   val p = Promise[ByteString]()
    //   val bsSource = Source.single(bs).concat(Source.fromFuture(p.future))

    //   Patch(
    //     s"/v2/$imageName/blobs/uploads/${blob.getId}",
    //     HttpEntity(ContentTypes.`application/octet-stream`, bsSource)
    //   ) ~> `Content-Range`(ContentRange(0L, bs.length - 1L)) ~> route ~> check {
    //       status shouldEqual StatusCodes.Accepted
    //       responseAs[String] shouldEqual ""
    //       header[Location].get shouldEqual Location(s"/v2/$imageName/blobs/${blob.getId}")
    //       header[`Docker-Upload-Uuid`].get shouldEqual `Docker-Upload-Uuid`(blob.getId)
    //       header[RangeCustom].get shouldEqual RangeCustom(0L, bs.length - 1)

    //       val updatedBlob = Await.result(PBlob.findByPk(blob.getId), 5.seconds).get
    //       updatedBlob.length shouldEqual bs.length
    //       updatedBlob.state shouldEqual BlobState.Uploading
    //       updatedBlob.sha256Digest shouldEqual Some(bsDigest)

    //       val file = BlobFile.getUploadFilePath(blob.getId)
    //       file.length shouldEqual bs.length
    //       val fis = new FileInputStream(file)
    //       val fileDigest = DigestUtils.sha256Hex(fis)
    //       fileDigest shouldEqual bsDigest
    //     }

    //   val blobRandom = ByteString(Random.random.alphanumeric.take(1024).mkString)

    //   Patch(
    //     s"/v2/$imageName/blobs/uploads/${blob.getId}",
    //     HttpEntity(ContentTypes.`application/octet-stream`, blobRandom)
    //   ) ~> `Content-Range`(ContentRange(0L, blobRandom.length - 1L)) ~> route ~> check {
    //       p.complete(Try(ByteString.empty))
    //       status shouldEqual StatusCodes.BadRequest // TODO
    //       // responseAs[String] shouldEqual ""
    //     }
    // }

    "return successful response for PUT request without final chunk to complete blob upload path" in {
      val blob = Fixtures.await(for {
        user ← Fixtures.User.create()
        blob ← Fixtures.docker.distribution.ImageBlob.createAs(
          user.getId, imageName, bs, ImageBlobState.Uploading
        )
      } yield blob)

      Put(
        s"/v2/$imageName/blobs/uploads/${blob.getId}?digest=sha256:$bsDigest",
        HttpEntity(`application/octet-stream`, bs)
      ) ~> addCredentials(credentials) ~> route ~> check {
          status shouldEqual StatusCodes.Created
          responseAs[ByteString] shouldBe empty
          header[Location].get shouldEqual Location(s"/v2/$imageName/blobs/sha256:$bsDigest")
          header[`Docker-Upload-Uuid`].map(h ⇒ UUID.fromString(h.value)) shouldEqual blob.id

          val b = Await.result(PImageBlob.findByPk(blob.getId), 5.seconds).get
          b.length shouldEqual bs.length
          b.state shouldEqual ImageBlobState.Uploaded
          b.sha256Digest shouldEqual Some(bsDigest)
        }
    }

    "return successful response for DELETE request to the blob upload path" in {
      val blob = Fixtures.await(for {
        user ← Fixtures.User.create()
        blob ← Fixtures.docker.distribution.ImageBlob.createAs(
          user.getId, imageName, bs, ImageBlobState.Uploading
        )
      } yield blob)

      Delete(s"/v2/$imageName/blobs/uploads/${blob.getId}") ~> addCredentials(credentials) ~> route ~> check {
        status shouldEqual StatusCodes.NoContent
        responseEntity shouldEqual HttpEntity.Empty

        val file = BlobFile.getUploadFilePath(blob.getId)
        file.exists() should be(false)
      }
    }
  }
}
