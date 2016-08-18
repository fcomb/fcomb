/*
 * Copyright 2016 fcomb. <https://fcomb.io>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.fcomb.docker.distribution.server.api

import akka.actor.PoisonPill
import akka.http.scaladsl._, model._
import akka.http.scaladsl.model.ContentTypes.`application/json`
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.model.headers._
import akka.http.scaladsl.testkit.ScalatestRouteTest
import akka.util.ByteString
import cats.data.Xor
import cats.scalatest.XorMatchers._
import de.heikoseeberger.akkahttpcirce.CirceSupport._
import io.circe.parser._
import io.circe.syntax._
import io.fcomb.docker.distribution.manifest.{SchemaV1 => SchemaV1Manifest}
import io.fcomb.docker.distribution.server.ContentTypes.{
  `application/vnd.docker.distribution.manifest.v1+prettyjws`,
  `application/vnd.docker.distribution.manifest.v2+json`
}
import io.fcomb.docker.distribution.server.Routes
import io.fcomb.docker.distribution.server.headers._
import io.fcomb.json.models.docker.distribution.CompatibleFormats._
import io.fcomb.json.models.docker.distribution.Formats._
import io.fcomb.json.models.errors.docker.distribution.Formats._
import io.fcomb.models.docker.distribution._
import io.fcomb.models.errors.docker.distribution.{DistributionErrorResponse, DistributionError}
import io.fcomb.persist.docker.distribution.ImageManifestsRepo
import io.fcomb.services.EventService
import io.fcomb.tests._
import io.fcomb.tests.fixtures._
import io.fcomb.tests.fixtures.docker.distribution._
import org.apache.commons.codec.digest.DigestUtils
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{Matchers, WordSpec}

class ImagesHandlerSpec
    extends WordSpec
    with Matchers
    with ScalatestRouteTest
    with SpecHelpers
    with ScalaFutures
    with PersistSpec
    with ActorClusterSpec {
  val route            = Routes()
  val imageName        = "test-image_2016"
  val bs               = ByteString(getFixture("docker/distribution/blob"))
  val bsDigest         = DigestUtils.sha256Hex(bs.toArray)
  val credentials      = BasicHttpCredentials(UsersRepoFixture.username, UsersRepoFixture.password)
  val apiVersionHeader = `Docker-Distribution-Api-Version`("2.0")
  val eventServiceRef  = EventService.start()

  override def afterAll(): Unit = {
    super.afterAll()
    eventServiceRef ! PoisonPill
  }

  "The image handler" should {
    "return list of repositories for GET request to the catalog path" in {
      val (image1Slug, image2Slug, image3Slug) = Fixtures.await(for {
        user   <- UsersRepoFixture.create()
        image1 <- ImagesRepoFixture.create(user, "test1", ImageVisibilityKind.Private)
        _      <- ImageBlobsRepoFixture.create(user.getId(), image1.getId())
        image2 <- ImagesRepoFixture.create(user, "test2", ImageVisibilityKind.Private)
        _      <- ImageBlobsRepoFixture.create(user.getId(), image2.getId())
        image3 <- ImagesRepoFixture.create(user, "test3", ImageVisibilityKind.Private)
        _      <- ImageBlobsRepoFixture.create(user.getId(), image3.getId())
      } yield (image1.slug, image2.slug, image3.slug))

      Get(s"/v2/_catalog") ~> addCredentials(credentials) ~> route ~> check {
        status shouldEqual StatusCodes.OK
        header[`Docker-Distribution-Api-Version`] should contain(apiVersionHeader)
        val resp = responseAs[DistributionImageCatalog]
        resp shouldEqual DistributionImageCatalog(Seq(image1Slug, image2Slug, image3Slug))
      }

      Get(s"/v2/_catalog?n=2") ~> addCredentials(credentials) ~> route ~> check {
        status shouldEqual StatusCodes.OK
        val uri = Uri(s"/v2/_catalog?n=2&last=$image2Slug")
        header[Link] should contain(Link(uri, LinkParams.next))
        val resp = responseAs[DistributionImageCatalog]
        resp shouldEqual DistributionImageCatalog(Seq(image1Slug, image2Slug))
      }

      Get(s"/v2/_catalog?n=2&last=$image2Slug") ~> addCredentials(credentials) ~> route ~> check {
        status shouldEqual StatusCodes.OK
        header[Link] shouldBe empty
        val resp = responseAs[DistributionImageCatalog]
        resp shouldEqual DistributionImageCatalog(Seq(image3Slug))
      }
    }

    "return list of tags for GET request to the tags path" in {
      val imageSlug = Fixtures.await(for {
        user  <- UsersRepoFixture.create()
        image <- ImagesRepoFixture.create(user, imageName, ImageVisibilityKind.Private)
        blob1 <- ImageBlobsRepoFixture.createAs(user.getId(),
                                                image.getId(),
                                                bs,
                                                ImageBlobState.Uploaded)
        imageSlug = image.slug
        _ <- ImageManifestsRepoFixture.createV2(user.getId(), imageSlug, blob1, List("1.0", "1.1"))
        blob2 <- ImageBlobsRepoFixture.createAs(user.getId(),
                                                image.getId(),
                                                bs ++ bs,
                                                ImageBlobState.Uploaded)
        _ <- ImageManifestsRepoFixture.createV2(user.getId(), imageSlug, blob2, List("2.0", "2.1"))
      } yield imageSlug)

      Get(s"/v2/$imageSlug/tags/list") ~> addCredentials(credentials) ~> route ~> check {
        status shouldEqual StatusCodes.OK
        header[`Docker-Distribution-Api-Version`] should contain(apiVersionHeader)
        val resp = responseAs[ImageTagsResponse]
        resp shouldEqual ImageTagsResponse(imageSlug, Vector("1.0", "1.1", "2.0", "2.1"))
      }

      Get(s"/v2/$imageSlug/tags/list?n=2") ~> addCredentials(credentials) ~> route ~> check {
        status shouldEqual StatusCodes.OK
        val uri = Uri(s"/v2/$imageSlug/tags/list?n=2&last=1.1")
        header[Link] should contain(Link(uri, LinkParams.next))
        val resp = responseAs[ImageTagsResponse]
        resp shouldEqual ImageTagsResponse(imageSlug, Vector("1.0", "1.1"))
      }

      Get(s"/v2/$imageSlug/tags/list?n=3&last=1.0") ~> addCredentials(credentials) ~> route ~> check {
        status shouldEqual StatusCodes.OK
        header[Link] shouldBe empty
        val resp = responseAs[ImageTagsResponse]
        resp shouldEqual ImageTagsResponse(imageSlug, Vector("1.1", "2.0", "2.1"))
      }
    }

    "return digest header for PUT request with schema v1 to manifest upload path" in {
      val manifestV1 = ByteString(getFixture("docker/distribution/manifestV1.json"))
      val digest     = "d3632f682f32ad9e7a66570167bf3b7c60fb2ea2f4ed9c3311023d38c2e1b2f3"

      val image = Fixtures.await(for {
        user  <- UsersRepoFixture.create()
        image <- ImagesRepoFixture.create(user, imageName, ImageVisibilityKind.Private)
        blob1 <- ImageBlobsRepoFixture.createAs(
          user.getId(),
          image.getId(),
          ByteString.empty,
          ImageBlobState.Uploaded,
          digestOpt = Some("09d0220f4043840bd6e2ab233cb2cb330195c9b49bb1f57c8f3fba1bfc90a309"))
      } yield image)

      Put(
        s"/v2/${image.slug}/manifests/sha256:$digest",
        HttpEntity(`application/json`, manifestV1)
      ) ~> addCredentials(credentials) ~> route ~> check {
        status shouldEqual StatusCodes.Created
        responseEntity shouldEqual HttpEntity.Empty
        header[`Docker-Distribution-Api-Version`] should contain(apiVersionHeader)
        header[`Docker-Content-Digest`] should contain(`Docker-Content-Digest`("sha256", digest))
        header[Location] should contain(Location(s"/v2/${image.slug}/manifests/sha256:$digest"))
      }
    }

    "return a failure reponse for PUT request with schema v1 to manifest upload path" in {
      val manifestV1 = ByteString(getFixture("docker/distribution/manifestV1.json"))
      val digest     = "d3632f682f32ad9e7a66570167bf3b7c60fb2ea2f4ed9c3311023d38c2e1b2f3"

      val imageSlug = Fixtures.await(for {
        u     <- UsersRepoFixture.create()
        image <- ImagesRepoFixture.create(u, imageName, ImageVisibilityKind.Private)
      } yield image.slug)

      Put(
        s"/v2/$imageSlug/manifests/sha256:$digest",
        HttpEntity(`application/json`, manifestV1)
      ) ~> addCredentials(credentials) ~> route ~> check {
        status shouldEqual StatusCodes.BadRequest
        val msg =
          s"Unknown blobs: sha256:09d0220f4043840bd6e2ab233cb2cb330195c9b49bb1f57c8f3fba1bfc90a309"
        val resp = responseAs[DistributionErrorResponse]
        resp.errors.head shouldEqual DistributionError.Unknown(msg)
      }
    }

    "return digest header for PUT request with schema v2 to manifest upload path" in {
      val manifestV2 = ByteString(getFixture("docker/distribution/manifestV2.json"))
      val digest     = "eeb39ca4e9565a6689fa1a6b79d130e058796359a1da88a6f3e3d0fc95ed3b0b"
      val configBlobBs = ByteString(getFixture(
        "docker/distribution/blob_sha256_13e1761bf172304ecf9b3fe05a653ceb7540973525e8ef83fb16c650b5410a08.json"))

      val (configBlob, imageSlug) = Fixtures.await(for {
        user  <- UsersRepoFixture.create()
        image <- ImagesRepoFixture.create(user, imageName, ImageVisibilityKind.Private)
        blob1 <- ImageBlobsRepoFixture.createAs(
          user.getId(),
          image.getId(),
          ByteString.empty,
          ImageBlobState.Uploaded,
          digestOpt = Some("d0ca440e86378344053c79282fe959c9f288ef2ab031411295d87ef1250cfec3"))
        cb <- ImageBlobsRepoFixture.createAs(user.getId(),
                                             image.getId(),
                                             configBlobBs,
                                             ImageBlobState.Uploaded)
      } yield (cb, image.slug))
      configBlob.digest should contain(
        "13e1761bf172304ecf9b3fe05a653ceb7540973525e8ef83fb16c650b5410a08")

      Put(
        s"/v2/$imageSlug/manifests/sha256:$digest",
        HttpEntity(`application/json`, manifestV2)
      ) ~> addCredentials(credentials) ~> route ~> check {
        status shouldEqual StatusCodes.Created
        responseEntity shouldEqual HttpEntity.Empty
        header[`Docker-Distribution-Api-Version`] should contain(apiVersionHeader)
        header[`Docker-Content-Digest`] should contain(`Docker-Content-Digest`("sha256", digest))
        header[Location] should contain(Location(s"/v2/$imageSlug/manifests/sha256:$digest"))
      }
    }

    "return a failure reponse for PUT request with schema v2 to manifest upload path" in {
      val manifestV2 = ByteString(getFixture("docker/distribution/manifestV2.json"))
      val digest     = "eeb39ca4e9565a6689fa1a6b79d130e058796359a1da88a6f3e3d0fc95ed3b0b"
      val configBlobBs = ByteString(getFixture(
        "docker/distribution/blob_sha256_13e1761bf172304ecf9b3fe05a653ceb7540973525e8ef83fb16c650b5410a08.json"))
      val configBlobDigest = "13e1761bf172304ecf9b3fe05a653ceb7540973525e8ef83fb16c650b5410a08"

      val (user, image) = Fixtures.await(for {
        u     <- UsersRepoFixture.create()
        image <- ImagesRepoFixture.create(u, imageName, ImageVisibilityKind.Private)
      } yield (u, image))
      val imageSlug = image.slug

      Put(
        s"/v2/$imageSlug/manifests/sha256:$digest",
        HttpEntity(`application/json`, manifestV2)
      ) ~> addCredentials(credentials) ~> route ~> check {
        status shouldEqual StatusCodes.BadRequest
        val msg  = s"Config blob `sha256:$configBlobDigest` not found"
        val resp = responseAs[DistributionErrorResponse]
        resp.errors.head shouldEqual DistributionError.Unknown(msg)
      }

      val configBlob = Fixtures.await(for {
        res <- ImageBlobsRepoFixture.createAs(
          user.getId(),
          image.getId(),
          configBlobBs,
          ImageBlobState.Uploaded
        )
      } yield res)
      configBlob.digest should contain(
        "13e1761bf172304ecf9b3fe05a653ceb7540973525e8ef83fb16c650b5410a08")

      Put(
        s"/v2/$imageSlug/manifests/sha256:$digest",
        HttpEntity(`application/json`, manifestV2)
      ) ~> addCredentials(credentials) ~> route ~> check {
        status shouldEqual StatusCodes.BadRequest
        val msg =
          "Unknown blobs: sha256:d0ca440e86378344053c79282fe959c9f288ef2ab031411295d87ef1250cfec3"
        val resp = responseAs[DistributionErrorResponse]
        resp.errors.head shouldEqual DistributionError.Unknown(msg)
      }
    }

    "return a manifest v1 for GET request to manifest path by tag and digest" in {
      val (im, image, imageSlug) = Fixtures.await(for {
        user  <- UsersRepoFixture.create()
        image <- ImagesRepoFixture.create(user, imageName, ImageVisibilityKind.Private)
        blob1 <- ImageBlobsRepoFixture.createAs(user.getId(),
                                                image.getId(),
                                                bs,
                                                ImageBlobState.Uploaded)
        imageSlug = image.slug
        im <- ImageManifestsRepoFixture.createV1(user.getId(), imageSlug, blob1, "1.0")
      } yield (im, image, imageSlug))

      def checkResponse(): Unit = {
        status shouldEqual StatusCodes.OK
        contentType shouldEqual `application/vnd.docker.distribution.manifest.v1+prettyjws`
        header[`Docker-Content-Digest`] should contain(
          `Docker-Content-Digest`("sha256", im.digest))
        header[ETag] should contain(ETag(s"sha256:${im.digest}"))
        header[`Docker-Distribution-Api-Version`] should contain(apiVersionHeader)
        val rawManifest         = responseAs[ByteString].utf8String
        val Xor.Right(manifest) = decode[SchemaV1.Manifest](rawManifest)
        manifest.tag shouldEqual "1.0"
        manifest.name shouldEqual image.slug
        val json         = parse(rawManifest).toOption.flatMap(_.asObject).get
        val manifestJson = json.remove("signatures").asJson
        val original     = SchemaV1Manifest.indentPrint(rawManifest, manifestJson)
        val res          = Xor.Right((original, im.digest))
        SchemaV1Manifest.verify(manifest, rawManifest) shouldBe res
      }

      Get(s"/v2/$imageSlug/manifests/sha256:${im.digest}") ~> addCredentials(credentials) ~> route ~> check {
        checkResponse()
      }

      Get(s"/v2/$imageSlug/manifests/1.0") ~> addCredentials(credentials) ~> route ~> check {
        checkResponse()
      }
    }

    "return a manifest v2 for GET request to manifest path by tag and digest" in {
      val (im, image, imageSlug) = Fixtures.await(for {
        user  <- UsersRepoFixture.create()
        image <- ImagesRepoFixture.create(user, imageName, ImageVisibilityKind.Private)
        blob1 <- ImageBlobsRepoFixture.createAs(user.getId(),
                                                image.getId(),
                                                bs,
                                                ImageBlobState.Uploaded)
        imageSlug = image.slug
        im <- ImageManifestsRepoFixture.createV2(user.getId(), imageSlug, blob1, List("1.0"))
      } yield (im, image, imageSlug))

      Get(s"/v2/$imageSlug/manifests/1.0") ~> addCredentials(credentials) ~> route ~> check {
        status shouldEqual StatusCodes.OK
        contentType shouldEqual `application/vnd.docker.distribution.manifest.v1+prettyjws`
        header[`Docker-Content-Digest`] should contain(
          `Docker-Content-Digest`("sha256", im.digest))
        header[ETag] should contain(ETag(s"sha256:${im.digest}"))
        header[`Docker-Distribution-Api-Version`] should contain(apiVersionHeader)
        val rawManifest         = responseAs[ByteString].utf8String
        val Xor.Right(manifest) = decode[SchemaV1.Manifest](rawManifest)
        manifest.tag shouldEqual "1.0"
        manifest.name shouldEqual image.slug
        SchemaV1Manifest.verify(manifest, rawManifest) should be(right)
      }

      def checkResponse(): Unit = {
        status shouldEqual StatusCodes.OK
        contentType shouldEqual `application/vnd.docker.distribution.manifest.v2+json`
        header[`Docker-Content-Digest`] should contain(
          `Docker-Content-Digest`("sha256", im.digest))
        header[ETag] should contain(ETag(s"sha256:${im.digest}"))
        header[`Docker-Distribution-Api-Version`] should contain(apiVersionHeader)
        val rawManifest         = responseAs[ByteString].utf8String
        val Xor.Right(manifest) = decode[SchemaV2.Manifest](rawManifest)
        manifest.config shouldEqual SchemaV2.Descriptor(
          Some("application/vnd.docker.container.image.v1+json"),
          11209,
          "sha256:a42e9dfd17b7e96bd880cb6e746c5bc8ef7611729164c74b010915fe7c224585"
        )
      }

      Get(s"/v2/$imageSlug/manifests/sha256:${im.digest}") ~> addCredentials(credentials) ~> route ~> check {
        checkResponse()
      }

      val mt = MediaType.applicationWithOpenCharset("vnd.docker.distribution.manifest.v2+json")
      Get(s"/v2/$imageSlug/manifests/1.0") ~> `Accept`(mt) ~> addCredentials(credentials) ~> route ~> check {
        checkResponse()
      }
    }

    "return an accepted for DELETE request to manifest path by digest" in {
      val (im, imageSlug) = Fixtures.await(for {
        user  <- UsersRepoFixture.create()
        image <- ImagesRepoFixture.create(user, imageName, ImageVisibilityKind.Private)
        blob1 <- ImageBlobsRepoFixture.createAs(user.getId(),
                                                image.getId(),
                                                bs,
                                                ImageBlobState.Uploaded)
        imageSlug = image.slug
        im <- ImageManifestsRepoFixture.createV2(user.getId(), imageSlug, blob1, List("1.0"))
      } yield (im, imageSlug))

      Delete(s"/v2/$imageSlug/manifests/sha256:${im.digest}") ~> addCredentials(credentials) ~> route ~> check {
        status shouldEqual StatusCodes.Accepted

        ImageManifestsRepo.findById(im.getId()).futureValue shouldBe empty
      }
    }
  }
}
