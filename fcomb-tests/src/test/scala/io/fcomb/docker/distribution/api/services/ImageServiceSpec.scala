package io.fcomb.docker.distribution.server.api.services

import io.fcomb.docker.distribution.server.api.services.headers._
import io.fcomb.persist.docker.distribution.{Blob ⇒ PBlob}
import io.fcomb.models.docker.distribution._
import io.fcomb.models.errors.docker.distribution._
import io.fcomb.json._
import io.fcomb.utils.{Config, StringUtils}
import io.fcomb.tests.{SpecHelpers, PersistSpec}
import io.fcomb.tests.fixtures.Fixtures
import scala.concurrent.Await
import scala.concurrent.duration._
import org.scalatest.{Matchers, WordSpec}
import org.scalatest.concurrent.ScalaFutures
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl._, model._
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.model.headers._
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import akka.http.scaladsl.testkit.ScalatestRouteTest
import akka.util.ByteString
import org.apache.commons.codec.digest.DigestUtils
import java.util.UUID
import java.io.{File, FileInputStream}
import java.security.MessageDigest
import spray.json._

class ImageServiceSpec extends WordSpec with Matchers with ScalatestRouteTest with SpecHelpers with ScalaFutures with PersistSpec {
  val route = Routes()
  val imageName = "library/test-image_2016"
  val bs = ByteString(getFixture("docker/distribution/blob"))
  val bsDigest = {
    val md = MessageDigest.getInstance("SHA-256")
    md.update(bs.toArray)
    StringUtils.hexify(md.digest)
  }
  val digest = "300f96719cd9297b942f67578f7e7fe0a4472f9c68c30aff78db728316279e6f"

  private def imageFile(imageName: String) =
    new File(s"${Config.docker.distribution.imageStorage}/${imageName.replaceAll("/", "_")}")

  "The image service" should {
    // "return a uuid for POST request to the start upload path" in {
    //   Fixtures.await(Fixtures.User.create())

    //   Post(s"/v2/$imageName/blobs/uploads/") ~> route ~> check {
    //     status shouldEqual StatusCodes.Accepted
    //     responseAs[String] shouldEqual ""
    //     val uuid = header[`Docker-Upload-Uuid`].map(h ⇒ UUID.fromString(h.value)).get
    //     header[Location].get shouldEqual Location(s"/v2/$imageName/blobs/uploads/$uuid")
    //     header[RangeCustom].get shouldEqual RangeCustom(0L, 0L)
    //   }
    // }

    // "return an info without content for HEAD request to the exist layer path" in {
    //   Fixtures.await(for {
    //     user ← Fixtures.User.create()
    //     _ ← Fixtures.DockerDistributionBlob.createWithImage(
    //       user.getId, imageName, digest, bs, bs.length
    //     )
    //   } yield ())

    //   Head(s"/v2/$imageName/blobs/sha256:$digest") ~> route ~> check {
    //     status shouldEqual StatusCodes.OK
    //     responseAs[String] shouldEqual ""
    //     header[`Docker-Content-Digest`].get shouldEqual `Docker-Content-Digest`("sha256", digest)
    //     // header[`Content-Length`].get shouldEqual bs.length
    //   }
    // }

    // "return a blob for GET request to the blob download path" in {
    //   Fixtures.await(for {
    //     user ← Fixtures.User.create()
    //     _ ← Fixtures.DockerDistributionBlob.createAsUploaded(
    //       user.getId, imageName, digest, bs, bs.length
    //     )
    //   } yield ())

    //   Get(s"/v2/$imageName/blobs/sha256:$digest") ~> route ~> check {
    //     status shouldEqual StatusCodes.OK
    //     responseAs[ByteString] shouldEqual bs
    //     header[`Docker-Content-Digest`].get shouldEqual `Docker-Content-Digest`("sha256", digest)
    //     // header[`Content-Length`].get shouldEqual bs.length
    //   }
    // }

    // "return successful response for POST request to initiate monolithic blob upload path" in {
    //   Fixtures.await(Fixtures.User.create())

    //   Post(
    //     s"/v2/$imageName/blobs/uploads/?digest=sha256:$bsDigest",
    //     HttpEntity(ContentTypes.`application/octet-stream`, bs)
    //   ) ~> route ~> check {
    //       status shouldEqual StatusCodes.Created
    //       responseAs[String] shouldEqual ""
    //       header[Location].get shouldEqual Location(s"/v2/$imageName/blobs/sha256:$bsDigest")
    //       val uuid = header[`Docker-Upload-Uuid`].map(h ⇒ UUID.fromString(h.value)).get

    //       val blob = Await.result(PBlob.findByPk(uuid), 5.seconds).get
    //       blob.length shouldEqual bs.length
    //       blob.state shouldEqual BlobState.Uploaded
    //       blob.sha256Digest shouldEqual Some(bsDigest)
    //     }
    // }

    // "return failed response for POST request to initiate monolithic blob upload path" in {
    //   Fixtures.await(Fixtures.User.create())

    //   Post(
    //     s"/v2/$imageName/blobs/uploads/?digest=sha256:333f96719cd9297b942f67578f7e7fe0a4472f9c68c30aff78db728316279e6f",
    //     HttpEntity(ContentTypes.`application/octet-stream`, bs)
    //   ) ~> route ~> check {
    //       status shouldEqual StatusCodes.BadRequest
    //       val resp = responseAs[JsValue].convertTo[DistributionErrorResponse]
    //       resp shouldEqual DistributionErrorResponse(Seq(DistributionError.DigestInvalid()))
    //     }
    // }

    // "return successful response for PUT request to monolithic blob upload path" in {
    //   val blob = Fixtures.await(for {
    //     user ← Fixtures.User.create()
    //     blob ← Fixtures.DockerDistributionBlob.create(user.getId, imageName)
    //   } yield blob)

    //   Put(
    //     s"/v2/$imageName/blobs/uploads/${blob.getId}?digest=sha256:$bsDigest",
    //     HttpEntity(ContentTypes.`application/octet-stream`, bs)
    //   ) ~> route ~> check {
    //       status shouldEqual StatusCodes.Created
    //       responseAs[String] shouldEqual ""
    //       header[Location].get shouldEqual Location(s"/v2/$imageName/blobs/sha256:$bsDigest")
    //       header[`Docker-Content-Digest`].get shouldEqual `Docker-Content-Digest`("sha256", bsDigest)
    //       val uuid = header[`Docker-Upload-Uuid`].map(h ⇒ UUID.fromString(h.value)).get

    //       val blob = Await.result(PBlob.findByPk(uuid), 5.seconds).get
    //       blob.length shouldEqual bs.length
    //       blob.state shouldEqual BlobState.Uploaded
    //       blob.sha256Digest shouldEqual Some(bsDigest)
    //           val file = imageFile(blob.getId.toString)
    // file.length shouldEqual bs.length
    // val fis = new FileInputStream(imageFile(blob.getId.toString))
    //   val fileDigest = DigestUtils.sha256Hex(fis)
    //   fileDigest shouldEqual bsDigest
    //     }
    // }

    "return successful response for PATCH requests to blob upload path" in {
      val blob = Fixtures.await(for {
        user ← Fixtures.User.create()
        blob ← Fixtures.DockerDistributionBlob.create(user.getId, imageName)
      } yield blob)

      Patch(
        s"/v2/$imageName/blobs/uploads/${blob.getId}",
        HttpEntity(ContentTypes.`application/octet-stream`, bs)
      ) ~> `Content-Range`(ContentRange(0L, bs.length - 1L)) ~> route ~> check {
          status shouldEqual StatusCodes.Accepted
          responseAs[String] shouldEqual ""
          header[Location].get shouldEqual Location(s"/v2/$imageName/blobs/${blob.getId}")
          header[`Docker-Upload-Uuid`].get shouldEqual `Docker-Upload-Uuid`(blob.getId)

          val updatedBlob = Await.result(PBlob.findByPk(blob.getId), 5.seconds).get
          updatedBlob.length shouldEqual bs.length
          updatedBlob.state shouldEqual BlobState.Uploading
          updatedBlob.sha256Digest shouldEqual None

          val file = imageFile(blob.getId.toString)
          file.length shouldEqual bs.length
          val fis = new FileInputStream(imageFile(blob.getId.toString))
          val fileDigest = DigestUtils.sha256Hex(fis)
          fileDigest shouldEqual bsDigest
        }
    }

    // "return successful response for PUT request without final chunk to complete blob upload path" in {
    //   val blob = Fixtures.await(for {
    //     user ← Fixtures.User.create()
    //     blob ← Fixtures.DockerDistributionBlob.createAsUploaded(
    //       user.getId, imageName, digest, bs, bs.length
    //     )
    //   } yield blob)

    //   Put(
    //     s"/v2/$imageName/blobs/uploads/${blob.getId}?digest=sha256:$bsDigest",
    //     HttpEntity(ContentTypes.`application/octet-stream`, bs)
    //   ) ~> route ~> check {
    //       status shouldEqual StatusCodes.Accepted
    //       responseAs[String] shouldEqual ""
    //       header[Location].get shouldEqual Location(s"/v2/$imageName/blobs/sha256:$bsDigest")
    //       val uuid = header[`Docker-Upload-Uuid`].map(h ⇒ UUID.fromString(h.value)).get

    //       val blob = Await.result(PBlob.findByPk(uuid), 5.seconds).get
    //       blob.length shouldEqual bs.length
    //       blob.state shouldEqual BlobState.Uploaded
    //       blob.sha256Digest shouldEqual Some(bsDigest)
    //     }
    // }
  }
}
