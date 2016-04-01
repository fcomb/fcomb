package io.fcomb.docker.distribution.server.api.services

import io.fcomb.docker.distribution.server.api.services.headers._
import io.fcomb.tests.{SpecHelpers, PersistSpec}
import io.fcomb.tests.fixtures.Fixtures
import org.scalatest.{Matchers, WordSpec}
import org.scalatest.concurrent.ScalaFutures
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.model.headers._
import akka.http.scaladsl.testkit.ScalatestRouteTest
import akka.util.ByteString
import java.util.UUID

class ImageServiceSpec extends WordSpec with Matchers with ScalatestRouteTest with SpecHelpers with ScalaFutures with PersistSpec {
  val route = Routes()
  val imageName = "library/test-image_2016"
  val bs = ByteString(getFixture("docker/distribution/blob"))
  val digest = "300f96719cd9297b942f67578f7e7fe0a4472f9c68c30aff78db728316279e6f"

  "The image service" should {
    "return a uuid for POST request to the start upload path" in {
      Fixtures.await(Fixtures.User.create())

      Post(s"/v2/$imageName/blobs/uploads/") ~> route ~> check {
        status shouldEqual StatusCodes.Accepted
        responseAs[String] shouldEqual ""
        val uuid = header[`Docker-Upload-Uuid`].map(h ⇒ UUID.fromString(h.value)).get
        header[Location].get shouldEqual Location(s"/v2/$imageName/blobs/uploads/$uuid")
        header[RangeCustom].get shouldEqual RangeCustom(0L, 0L)
      }
    }

    "return an info without content for HEAD request to the exist layer path" in {
      Fixtures.await(for {
        user ← Fixtures.User.create()
        _ ← Fixtures.DockerDistributionBlob.createWithImage(
          user.getId, imageName, digest, bs, bs.length
        )
      } yield ())

      Head(s"/v2/$imageName/blobs/sha256:$digest") ~> route ~> check {
        status shouldEqual StatusCodes.OK
        responseAs[String] shouldEqual ""
        header[`Docker-Content-Digest`].get shouldEqual `Docker-Content-Digest`("sha256", digest)
        // header[`Content-Length`].get shouldEqual bs.length
      }
    }

    "return a blob for GET request to the blob download path" in {
      Fixtures.await(for {
        user ← Fixtures.User.create()
        _ ← Fixtures.DockerDistributionBlob.createWithImage(
          user.getId, imageName, digest, bs, bs.length
        )
      } yield ())

      Get(s"/v2/$imageName/blobs/sha256:$digest") ~> route ~> check {
        status shouldEqual StatusCodes.OK
        responseAs[ByteString] shouldEqual bs
        header[`Docker-Content-Digest`].get shouldEqual `Docker-Content-Digest`("sha256", digest)
        // header[`Content-Length`].get shouldEqual bs.length
      }
    }
  }
}
