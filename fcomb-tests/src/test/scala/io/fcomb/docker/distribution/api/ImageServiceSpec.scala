package io.fcomb.docker.distribution.server.api

import akka.http.scaladsl._, model._
import akka.http.scaladsl.model.ContentTypes.`application/json`
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.model.headers._
import akka.http.scaladsl.testkit.ScalatestRouteTest
import akka.util.ByteString
import cats.data.Xor
import cats.scalatest.XorMatchers._
import de.heikoseeberger.akkahttpcirce.CirceSupport._
import io.circe.generic.auto._
import io.circe.parser.decode
import io.fcomb.docker.distribution.manifest.{SchemaV1 ⇒ SchemaV1Manifest}
import io.fcomb.docker.distribution.server.api.ContentTypes.{`application/vnd.docker.distribution.manifest.v1+prettyjws`, `application/vnd.docker.distribution.manifest.v2+json`}
import io.fcomb.docker.distribution.server.api.headers._
import io.fcomb.json.docker.distribution.Formats._
import io.fcomb.models.docker.distribution._
import io.fcomb.models.errors.docker.distribution.{DistributionErrorResponse, DistributionError}
import io.fcomb.persist.docker.distribution.{ImageManifest ⇒ PImageManifest}
import io.fcomb.tests._
import io.fcomb.tests.fixtures.Fixtures
import org.apache.commons.codec.digest.DigestUtils
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{Matchers, WordSpec}

class ImageServiceSpec extends WordSpec with Matchers with ScalatestRouteTest with SpecHelpers with ScalaFutures with PersistSpec with ActorClusterSpec {
  val route = Routes()
  val imageName = "library/test-image_2016"
  val bs = ByteString(getFixture("docker/distribution/blob"))
  val bsDigest = DigestUtils.sha256Hex(bs.toArray)
  val credentials = BasicHttpCredentials(Fixtures.User.username, Fixtures.User.password)
  val apiVersionHeader = `Docker-Distribution-Api-Version`("2.0")

  "The image service" should {
    "return list of repositories for GET request to the catalog path" in {
      Fixtures.await(for {
        user ← Fixtures.User.create()
        _ ← Fixtures.docker.distribution.ImageBlob.create(user.getId, "first/test1")
        _ ← Fixtures.docker.distribution.ImageBlob.create(user.getId, "second/test2")
        _ ← Fixtures.docker.distribution.ImageBlob.create(user.getId, "third/test3")
      } yield ())

      Get(s"/v2/_catalog") ~> addCredentials(credentials) ~> route ~> check {
        status shouldEqual StatusCodes.OK
        header[`Docker-Distribution-Api-Version`] should contain(apiVersionHeader)
        val resp = responseAs[DistributionImageCatalog]
        resp shouldEqual DistributionImageCatalog(Seq("first/test1", "second/test2", "third/test3"))
      }

      Get(s"/v2/_catalog?n=2") ~> addCredentials(credentials) ~> route ~> check {
        status shouldEqual StatusCodes.OK
        val uri = Uri(s"/v2/_catalog?n=2&last=second/test2")
        header[Link] should contain(Link(uri, LinkParams.next))
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
        _ ← Fixtures.docker.distribution.ImageManifest.createV2(user.getId, imageName, blob1, List("1.0", "1.1"))
        blob2 ← Fixtures.docker.distribution.ImageBlob.createAs(user.getId, imageName, bs ++ bs, ImageBlobState.Uploaded)
        _ ← Fixtures.docker.distribution.ImageManifest.createV2(user.getId, imageName, blob2, List("2.0", "2.1"))
      } yield ())

      Get(s"/v2/$imageName/tags/list") ~> addCredentials(credentials) ~> route ~> check {
        status shouldEqual StatusCodes.OK
        header[`Docker-Distribution-Api-Version`] should contain(apiVersionHeader)
        val resp = responseAs[ImageTagsResponse]
        resp shouldEqual ImageTagsResponse(imageName, Vector("1.0", "1.1", "2.0", "2.1"))
      }

      Get(s"/v2/$imageName/tags/list?n=2") ~> addCredentials(credentials) ~> route ~> check {
        status shouldEqual StatusCodes.OK
        val uri = Uri(s"/v2/$imageName/tags/list?n=2&last=1.1")
        header[Link] should contain(Link(uri, LinkParams.next))
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

    "return digest header for PUT request with schema v1 to manifest upload path" in {
      val manifestV1 = ByteString(getFixture("docker/distribution/manifestV1.json"))
      val digest = "d3632f682f32ad9e7a66570167bf3b7c60fb2ea2f4ed9c3311023d38c2e1b2f3"

      Fixtures.await(for {
        user ← Fixtures.User.create()
        blob1 ← Fixtures.docker.distribution.ImageBlob.createAs(user.getId, imageName,
          ByteString.empty, ImageBlobState.Uploaded,
          digestOpt = Some("09d0220f4043840bd6e2ab233cb2cb330195c9b49bb1f57c8f3fba1bfc90a309"))
      } yield ())

      Put(
        s"/v2/$imageName/manifests/sha256:$digest",
        HttpEntity(`application/json`, manifestV1)
      ) ~> addCredentials(credentials) ~> route ~> check {
          status shouldEqual StatusCodes.Created
          responseEntity shouldEqual HttpEntity.Empty
          header[`Docker-Distribution-Api-Version`] should contain(apiVersionHeader)
          header[`Docker-Content-Digest`] should contain(`Docker-Content-Digest`("sha256", digest))
          header[Location] should contain(Location(s"/v2/$imageName/manifests/sha256:$digest"))
        }
    }

    "return a failure reponse for PUT request with schema v1 to manifest upload path" in {
      val manifestV1 = ByteString(getFixture("docker/distribution/manifestV1.json"))
      val digest = "d3632f682f32ad9e7a66570167bf3b7c60fb2ea2f4ed9c3311023d38c2e1b2f3"

      Fixtures.await(for {
        u ← Fixtures.User.create()
        _ ← Fixtures.docker.distribution.Image.create(u.getId, imageName)
      } yield u)

      Put(
        s"/v2/$imageName/manifests/sha256:$digest",
        HttpEntity(`application/json`, manifestV1)
      ) ~> addCredentials(credentials) ~> route ~> check {
          status shouldEqual StatusCodes.BadRequest
          val msg = s"Unknown blobs: sha256:09d0220f4043840bd6e2ab233cb2cb330195c9b49bb1f57c8f3fba1bfc90a309"
          val resp = responseAs[DistributionErrorResponse]
          resp.errors.head shouldEqual DistributionError.Unknown(msg)
        }
    }

    "return digest header for PUT request with schema v2 to manifest upload path" in {
      val manifestV2 = ByteString(getFixture("docker/distribution/manifestV2.json"))
      val digest = "eeb39ca4e9565a6689fa1a6b79d130e058796359a1da88a6f3e3d0fc95ed3b0b"
      val configBlobBs = ByteString(getFixture("docker/distribution/blob_sha256_13e1761bf172304ecf9b3fe05a653ceb7540973525e8ef83fb16c650b5410a08.json"))

      val configBlob = Fixtures.await(for {
        user ← Fixtures.User.create()
        blob1 ← Fixtures.docker.distribution.ImageBlob.createAs(user.getId, imageName,
          ByteString.empty, ImageBlobState.Uploaded,
          digestOpt = Some("d0ca440e86378344053c79282fe959c9f288ef2ab031411295d87ef1250cfec3"))
        cb ← Fixtures.docker.distribution.ImageBlob.createAs(user.getId, imageName,
          configBlobBs, ImageBlobState.Uploaded)
      } yield cb)
      configBlob.sha256Digest should contain("13e1761bf172304ecf9b3fe05a653ceb7540973525e8ef83fb16c650b5410a08")

      Put(
        s"/v2/$imageName/manifests/sha256:$digest",
        HttpEntity(`application/json`, manifestV2)
      ) ~> addCredentials(credentials) ~> route ~> check {
          status shouldEqual StatusCodes.Created
          responseEntity shouldEqual HttpEntity.Empty
          header[`Docker-Distribution-Api-Version`] should contain(apiVersionHeader)
          header[`Docker-Content-Digest`] should contain(`Docker-Content-Digest`("sha256", digest))
          header[Location] should contain(Location(s"/v2/$imageName/manifests/sha256:$digest"))
        }
    }

    "return a failure reponse for PUT request with schema v2 to manifest upload path" in {
      val manifestV2 = ByteString(getFixture("docker/distribution/manifestV2.json"))
      val digest = "eeb39ca4e9565a6689fa1a6b79d130e058796359a1da88a6f3e3d0fc95ed3b0b"
      val configBlobBs = ByteString(getFixture("docker/distribution/blob_sha256_13e1761bf172304ecf9b3fe05a653ceb7540973525e8ef83fb16c650b5410a08.json"))
      val configBlobDigest = "13e1761bf172304ecf9b3fe05a653ceb7540973525e8ef83fb16c650b5410a08"

      val user = Fixtures.await(for {
        u ← Fixtures.User.create()
        _ ← Fixtures.docker.distribution.Image.create(u.getId, imageName)
      } yield u)

      Put(
        s"/v2/$imageName/manifests/sha256:$digest",
        HttpEntity(`application/json`, manifestV2)
      ) ~> addCredentials(credentials) ~> route ~> check {
          status shouldEqual StatusCodes.BadRequest
          val msg = s"Config blob `sha256:$configBlobDigest` not found"
          val resp = responseAs[DistributionErrorResponse]
          resp.errors.head shouldEqual DistributionError.Unknown(msg)
        }

      val configBlob = Fixtures.await(Fixtures.docker.distribution.ImageBlob.createAs(
        user.getId, imageName, configBlobBs, ImageBlobState.Uploaded
      ))
      configBlob.sha256Digest should contain("13e1761bf172304ecf9b3fe05a653ceb7540973525e8ef83fb16c650b5410a08")

      Put(
        s"/v2/$imageName/manifests/sha256:$digest",
        HttpEntity(`application/json`, manifestV2)
      ) ~> addCredentials(credentials) ~> route ~> check {
          status shouldEqual StatusCodes.BadRequest
          val msg = "Unknown blobs: sha256:d0ca440e86378344053c79282fe959c9f288ef2ab031411295d87ef1250cfec3"
          val resp = responseAs[DistributionErrorResponse]
          resp.errors.head shouldEqual DistributionError.Unknown(msg)
        }
    }

    "return a manifest v1 for GET request to manifest path by tag and digest" in {
      val im = Fixtures.await(for {
        user ← Fixtures.User.create()
        blob1 ← Fixtures.docker.distribution.ImageBlob.createAs(user.getId, imageName, bs, ImageBlobState.Uploaded)
        im ← Fixtures.docker.distribution.ImageManifest.createV1(user.getId, imageName, blob1, "1.0")
      } yield im)

      def checkResponse(): Unit = {
        status shouldEqual StatusCodes.OK
        contentType shouldEqual `application/vnd.docker.distribution.manifest.v1+prettyjws`
        header[`Docker-Content-Digest`] should contain(`Docker-Content-Digest`("sha256", im.sha256Digest))
        header[ETag] should contain(ETag(s"sha256:${im.sha256Digest}"))
        header[`Docker-Distribution-Api-Version`] should contain(apiVersionHeader)
        val rawManifest = responseAs[ByteString].utf8String
        val Xor.Right(manifest) = decode[SchemaV1.Manifest](rawManifest)
        manifest.tag shouldEqual "1.0"
        SchemaV1Manifest.verify(manifest, rawManifest) shouldBe (Xor.right((rawManifest, im.sha256Digest)))
      }

      Get(s"/v2/$imageName/manifests/sha256:${im.sha256Digest}") ~> addCredentials(credentials) ~> route ~> check {
        checkResponse()
      }

      Get(s"/v2/$imageName/manifests/1.0") ~> addCredentials(credentials) ~> route ~> check {
        checkResponse()
      }
    }

    "return a manifest v2 for GET request to manifest path by tag and digest" in {
      val im = Fixtures.await(for {
        user ← Fixtures.User.create()
        blob1 ← Fixtures.docker.distribution.ImageBlob.createAs(user.getId, imageName, bs, ImageBlobState.Uploaded)
        im ← Fixtures.docker.distribution.ImageManifest.createV2(user.getId, imageName, blob1, List("1.0"))
      } yield im)

      Get(s"/v2/$imageName/manifests/1.0") ~> addCredentials(credentials) ~> route ~> check {
        status shouldEqual StatusCodes.OK
        contentType shouldEqual `application/vnd.docker.distribution.manifest.v1+prettyjws`
        header[`Docker-Content-Digest`] should contain(`Docker-Content-Digest`("sha256", im.sha256Digest))
        header[ETag] should contain(ETag(s"sha256:${im.sha256Digest}"))
        header[`Docker-Distribution-Api-Version`] should contain(apiVersionHeader)
        val rawManifest = responseAs[ByteString].utf8String
        val Xor.Right(manifest) = decode[SchemaV1.Manifest](rawManifest)
        manifest.tag shouldEqual "1.0"
        SchemaV1Manifest.verify(manifest, rawManifest) should be(right)
      }

      def checkResponse(): Unit = {
        status shouldEqual StatusCodes.OK
        contentType shouldEqual `application/vnd.docker.distribution.manifest.v2+json`
        header[`Docker-Content-Digest`] should contain(`Docker-Content-Digest`("sha256", im.sha256Digest))
        header[ETag] should contain(ETag(s"sha256:${im.sha256Digest}"))
        header[`Docker-Distribution-Api-Version`] should contain(apiVersionHeader)
        val rawManifest = responseAs[ByteString].utf8String
        val Xor.Right(manifest) = decode[SchemaV2.Manifest](rawManifest)
        manifest.config shouldEqual SchemaV2.Descriptor(
          Some("application/vnd.docker.container.image.v1+json"), 11209,
          "sha256:a42e9dfd17b7e96bd880cb6e746c5bc8ef7611729164c74b010915fe7c224585"
        )
      }

      Get(s"/v2/$imageName/manifests/sha256:${im.sha256Digest}") ~> addCredentials(credentials) ~> route ~> check {
        checkResponse()
      }

      val mt = MediaType.applicationWithOpenCharset("vnd.docker.distribution.manifest.v2+json")
      Get(s"/v2/$imageName/manifests/1.0") ~> `Accept`(mt) ~> addCredentials(credentials) ~> route ~> check {
        checkResponse()
      }
    }

    "return an accepted for DELETE request to manifest path by digest" in {
      val im = Fixtures.await(for {
        user ← Fixtures.User.create()
        blob1 ← Fixtures.docker.distribution.ImageBlob.createAs(user.getId, imageName, bs, ImageBlobState.Uploaded)
        im ← Fixtures.docker.distribution.ImageManifest.createV2(user.getId, imageName, blob1, List("1.0"))
      } yield im)

      Delete(s"/v2/$imageName/manifests/sha256:${im.sha256Digest}") ~> addCredentials(credentials) ~> route ~> check {
        status shouldEqual StatusCodes.Accepted

        ImageManifestsRepo.findByPk(im.getId).futureValue shouldBe empty
      }
    }
  }
}
