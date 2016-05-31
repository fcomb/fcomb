package io.fcomb.docker.distribution.server.api

import akka.http.scaladsl._, model._
import akka.http.scaladsl.model.ContentTypes.`application/json`
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

class ImageServiceSpec extends WordSpec with Matchers with ScalatestRouteTest with SpecHelpers with ScalaFutures with PersistSpec with ActorClusterSpec {
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

  "The image service" should {
    // "return an info without content for HEAD request to the exist layer path" in {
    //   Fixtures.await(for {
    //     user ← Fixtures.User.create()
    //     _ ← Fixtures.docker.distribution.ImageBlob.createWithImage(
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

    "return list of repositories for GET request to the catalog path" in {
      import ImageService.DistributionImageCatalog

      Fixtures.await(for {
        user ← Fixtures.User.create()
        _ ← Fixtures.docker.distribution.ImageBlob.create(user.getId, "first/test1")
        _ ← Fixtures.docker.distribution.ImageBlob.create(user.getId, "second/test2")
        _ ← Fixtures.docker.distribution.ImageBlob.create(user.getId, "third/test3")
      } yield ())

      Get(s"/v2/_catalog") ~> addCredentials(credentials) ~> route ~> check {
        status shouldEqual StatusCodes.OK
        val resp = responseAs[DistributionImageCatalog]
        resp shouldEqual DistributionImageCatalog(Seq("first/test1", "second/test2", "third/test3"))
      }

      Get(s"/v2/_catalog?n=2") ~> addCredentials(credentials) ~> route ~> check {
        status shouldEqual StatusCodes.OK
        val uri = Uri(s"/v2/_catalog?n=2&last=second/test2")
        header[Link].get shouldEqual Link(uri, LinkParams.next)
        val resp = responseAs[DistributionImageCatalog]
        resp shouldEqual DistributionImageCatalog(Seq("first/test1", "second/test2"))
      }

      Get(s"/v2/_catalog?n=2&last=second/test2") ~> addCredentials(credentials) ~> route ~> check {
        status shouldEqual StatusCodes.OK
        header[Link] shouldBe empty
        val resp = responseAs[DistributionImageCatalog]
        resp shouldEqual DistributionImageCatalog(Seq("third/test3"))
      }
    }

    "return list of tags for GET request to the tags path" in {
      Fixtures.await(for {
        user ← Fixtures.User.create()
        blob1 ← Fixtures.docker.distribution.ImageBlob.createAs(user.getId, imageName, bs, ImageBlobState.Uploaded)
        _ ← Fixtures.docker.distribution.ImageManifest.create(user.getId, imageName, blob1, List("1.0", "1.1"))
        blob2 ← Fixtures.docker.distribution.ImageBlob.createAs(user.getId, imageName, bs ++ bs, ImageBlobState.Uploaded)
        _ ← Fixtures.docker.distribution.ImageManifest.create(user.getId, imageName, blob2, List("2.0", "2.1"))
      } yield ())

      Get(s"/v2/$imageName/tags/list") ~> addCredentials(credentials) ~> route ~> check {
        status shouldEqual StatusCodes.OK
        val resp = responseAs[ImageTagsResponse]
        resp shouldEqual ImageTagsResponse(imageName, Vector("1.0", "1.1", "2.0", "2.1"))
      }

      Get(s"/v2/$imageName/tags/list?n=2") ~> addCredentials(credentials) ~> route ~> check {
        status shouldEqual StatusCodes.OK
        val uri = Uri(s"/v2/$imageName/tags/list?n=2&last=1.1")
        header[Link].get shouldEqual Link(uri, LinkParams.next)
        val resp = responseAs[ImageTagsResponse]
        resp shouldEqual ImageTagsResponse(imageName, Vector("1.0", "1.1"))
      }

      Get(s"/v2/$imageName/tags/list?n=3&last=1.0") ~> addCredentials(credentials) ~> route ~> check {
        status shouldEqual StatusCodes.OK
        header[Link] shouldBe empty
        val resp = responseAs[ImageTagsResponse]
        resp shouldEqual ImageTagsResponse(imageName, Vector("1.1", "2.0", "2.1"))
      }
    }

    "return digest header for PUT request to manifest upload path" in {
      val manifestV1 = ByteString(getFixture("docker/distribution/manifestV1.json"))

      Fixtures.await(for {
        user ← Fixtures.User.create()
        blob1 ← Fixtures.docker.distribution.ImageBlob.createAs(user.getId, imageName, bs, ImageBlobState.Uploaded)
      } yield ())

      Put(
        s"/v2/$imageName/manifests/sha256:xxxxxxxx",
        HttpEntity(`application/json`, manifestV1)
      ) ~> addCredentials(credentials) ~> route ~> check {
          println(response)
          status shouldEqual StatusCodes.OK
        }
    }
  }
}
