package io.fcomb.docker.distribution.server.api

import akka.http.scaladsl._, model._
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.model.headers._
import akka.http.scaladsl.testkit.ScalatestRouteTest
import akka.util.ByteString
import de.heikoseeberger.akkahttpcirce.CirceSupport._
import io.circe.generic.auto._
import io.fcomb.docker.distribution.server.api.headers._
import io.fcomb.docker.distribution.server.utils.BlobFile
import io.fcomb.json.docker.distribution.Formats._
import io.fcomb.models.docker.distribution._
import io.fcomb.tests._
import io.fcomb.tests.fixtures.Fixtures
import io.fcomb.utils.StringUtils
import java.security.MessageDigest
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{Matchers, WordSpec}

class ImageBlobServiceSpec extends WordSpec with Matchers with ScalatestRouteTest with SpecHelpers with ScalaFutures with PersistSpec with ActorClusterSpec {
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

  "The image blob service" should {
    "return an info without content for HEAD request to the exist layer path" in {
      Fixtures.await(for {
        user ← Fixtures.User.create()
        _ ← Fixtures.docker.distribution.ImageBlob.createAs(
          user.getId, imageName, bs, ImageBlobState.Uploaded
        )
      } yield ())

      Head(s"/v2/$imageName/blobs/sha256:$bsDigest") ~> addCredentials(credentials) ~> route ~> check {
        status shouldEqual StatusCodes.OK
        responseAs[ByteString] shouldBe empty
        header[`Docker-Content-Digest`].get shouldEqual `Docker-Content-Digest`("sha256", bsDigest)
        // header[`Content-Length`].get shouldEqual bs.length
      }
    }

    "return a blob for GET request to the blob download path" in {
      Fixtures.await(for {
        user ← Fixtures.User.create()
        _ ← Fixtures.docker.distribution.ImageBlob.createAs(
          user.getId, imageName, bs, ImageBlobState.Uploaded
        )
      } yield ())

      Get(s"/v2/$imageName/blobs/sha256:$bsDigest") ~> addCredentials(credentials) ~> route ~> check {
        status shouldEqual StatusCodes.OK
        responseAs[ByteString] shouldEqual bs
        header[`Docker-Content-Digest`].get shouldEqual `Docker-Content-Digest`("sha256", bsDigest)
        // header[`Content-Length`].get shouldEqual bs.length
      }
    }

    "return not modified status for GET request to the blob download path" in {
      Fixtures.await(for {
        user ← Fixtures.User.create()
        _ ← Fixtures.docker.distribution.ImageBlob.createAs(
          user.getId, imageName, bs, ImageBlobState.Uploaded
        )
      } yield ())
      val headers = `If-None-Match`(EntityTag(s"sha256:$bsDigest"))

      Get(s"/v2/$imageName/blobs/sha256:$bsDigest") ~> headers ~> addCredentials(credentials) ~> route ~> check {
        status shouldEqual StatusCodes.NotModified
        responseAs[ByteString] shouldBe empty
      }
    }

    "return successful response for DELETE request to the blob path" in {
      val blob = Fixtures.await(for {
        user ← Fixtures.User.create()
        blob ← Fixtures.docker.distribution.ImageBlob.createAs(
          user.getId, imageName, bs, ImageBlobState.Uploaded
        )
      } yield blob)

      Delete(s"/v2/$imageName/blobs/sha256:${blob.sha256Digest.get}") ~> addCredentials(credentials) ~> route ~> check {
        status shouldEqual StatusCodes.NoContent
        responseEntity shouldEqual HttpEntity.Empty

        val file = BlobFile.getUploadFilePath(blob.getId)
        file.exists() should be(false)
      }
    }

  }
}
