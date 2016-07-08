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
import akka.http.scaladsl.model.ContentTypes.`application/octet-stream`
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.model.headers._
import akka.http.scaladsl.testkit.ScalatestRouteTest
import akka.stream.scaladsl.Source
import akka.util.ByteString
import de.heikoseeberger.akkahttpcirce.CirceSupport._
import io.circe.generic.auto._
import io.fcomb.docker.distribution.server.headers._
import io.fcomb.docker.distribution.services.ImageBlobPushProcessor
import io.fcomb.docker.distribution.utils.BlobFileUtils
import io.fcomb.json.models.docker.distribution.CompatibleFormats._
import io.fcomb.models.docker.distribution._
import io.fcomb.models.errors.docker.distribution._
import io.fcomb.persist.docker.distribution._
import io.fcomb.tests._
import io.fcomb.tests.fixtures._
import io.fcomb.tests.fixtures.docker.distribution.{ImageBlobsRepoFixture, ImagesRepoFixture}
import java.io.FileInputStream
import java.util.UUID
import org.apache.commons.codec.digest.DigestUtils
import org.scalatest.{Matchers, WordSpec}
import scala.concurrent.duration._
import io.fcomb.docker.distribution.server.Routes
import akka.http.scaladsl.testkit.RouteTestTimeout

class ImageBlobUploadsHandlerSpec
    extends WordSpec
    with Matchers
    with ScalatestRouteTest
    with SpecHelpers
    with PersistSpec
    with ActorClusterSpec
    with FutureSpec {
  implicit val routeTimeout = RouteTestTimeout(5.seconds)

  val route            = Routes()
  val imageName        = "test-image_2016"
  val bs               = ByteString(getFixture("docker/distribution/blob"))
  val bsDigest         = DigestUtils.sha256Hex(bs.toArray)
  val credentials      = BasicHttpCredentials(UsersRepoFixture.username, UsersRepoFixture.password)
  val clusterRef       = ImageBlobPushProcessor.startRegion(30.seconds)
  val apiVersionHeader = `Docker-Distribution-Api-Version`("2.0")

  override def afterAll(): Unit = {
    super.afterAll()
    clusterRef ! PoisonPill
  }

  override def beforeEach(): Unit = {
    super.beforeEach()
    BlobFileUtils.getBlobFilePath(bsDigest).delete()
  }

  "The image blob upload handler" should {
    "return a uuid for POST request to the start upload path" in {
      val imageSlug = Fixtures.await(for {
        user  <- UsersRepoFixture.create()
        image <- ImagesRepoFixture.create(user, imageName, ImageVisibilityKind.Private)
      } yield image.slug)

      Post(s"/v2/$imageSlug/blobs/uploads/") ~> addCredentials(credentials) ~> route ~> check {
        status shouldEqual StatusCodes.Accepted
        responseEntity shouldEqual HttpEntity.Empty
        val uuid = header[`Docker-Upload-Uuid`].map(h => UUID.fromString(h.value)).get
        header[Location] should contain(Location(s"/v2/$imageSlug/blobs/uploads/$uuid"))
        header[RangeCustom] should contain(RangeCustom(0L, 0L))
        header[`Docker-Distribution-Api-Version`] should contain(apiVersionHeader)
      }
    }

    "return successful response for POST request to initiate monolithic blob upload path" in {
      val imageSlug = Fixtures.await(for {
        user  <- UsersRepoFixture.create()
        image <- ImagesRepoFixture.create(user, imageName, ImageVisibilityKind.Private)
      } yield image.slug)

      Post(
        s"/v2/$imageSlug/blobs/uploads/?digest=sha256:$bsDigest",
        HttpEntity(`application/octet-stream`, bs)
      ) ~> addCredentials(credentials) ~> route ~> check {
        status shouldEqual StatusCodes.Created
        responseEntity shouldEqual HttpEntity.Empty
        header[Location] should contain(Location(s"/v2/$imageSlug/blobs/sha256:$bsDigest"))
        header[`Docker-Content-Digest`] should contain(`Docker-Content-Digest`("sha256", bsDigest))
        header[`Docker-Distribution-Api-Version`] should contain(apiVersionHeader)
        val uuid = header[`Docker-Upload-Uuid`].map(h => UUID.fromString(h.value)).get

        val blob = await(ImageBlobsRepo.findById(uuid)).get
        blob.length shouldEqual bs.length
        blob.state shouldEqual ImageBlobState.Uploaded
        blob.sha256Digest shouldEqual Some(bsDigest)

        val file = BlobFileUtils.getBlobFilePath(blob.sha256Digest.get)
        file.length shouldEqual bs.length
        val fis        = new FileInputStream(file)
        val fileDigest = DigestUtils.sha256Hex(fis)
        fileDigest shouldEqual bsDigest
      }
    }

    "return failed response for POST request to initiate monolithic blob upload path" in {
      val imageSlug = Fixtures.await(for {
        user  <- UsersRepoFixture.create()
        image <- ImagesRepoFixture.create(user, imageName, ImageVisibilityKind.Private)
      } yield image.slug)

      Post(
        s"/v2/$imageSlug/blobs/uploads/?digest=sha256:333f96719cd9297b942f67578f7e7fe0a4472f9c68c30aff78db728316279e6f",
        HttpEntity(`application/octet-stream`, bs)
      ) ~> addCredentials(credentials) ~> route ~> check {
        status shouldEqual StatusCodes.BadRequest
        val resp = responseAs[DistributionErrorResponse]
        resp shouldEqual DistributionErrorResponse(Seq(DistributionError.DigestInvalid()))
      }
    }

    "return successful response for PUT request to mount blob upload path" in {
      val (imageSlug, mountImage) = Fixtures.await(for {
        user  <- UsersRepoFixture.create()
        image <- ImagesRepoFixture.create(user, imageName, ImageVisibilityKind.Private)
        blob <- ImageBlobsRepoFixture.createAs(
                 user.getId(),
                 image.getId(),
                 bs,
                 ImageBlobState.Uploaded
               )
        mountImage <- ImagesRepoFixture.create(user,
                                               s"${imageName}-mount",
                                               ImageVisibilityKind.Private)
      } yield (image.slug, mountImage))

      Post(
        s"/v2/${mountImage.slug}/blobs/uploads/?mount=sha256:$bsDigest&from=$imageSlug",
        HttpEntity(`application/octet-stream`, bs)
      ) ~> addCredentials(credentials) ~> route ~> check {
        status shouldEqual StatusCodes.Created
        responseEntity shouldEqual HttpEntity.Empty
        header[Location] should contain(Location(s"/v2/${mountImage.slug}/blobs/sha256:$bsDigest"))
        header[`Docker-Content-Digest`] should contain(`Docker-Content-Digest`("sha256", bsDigest))
        header[`Docker-Distribution-Api-Version`] should contain(apiVersionHeader)

        val newBlob = await({
          for {
            Some(blob) <- ImageBlobsRepo.findByImageIdAndDigest(mountImage.getId(), bsDigest)
          } yield blob
        })
        newBlob.length shouldEqual bs.length
        newBlob.state shouldEqual ImageBlobState.Uploaded
        newBlob.sha256Digest shouldEqual Some(bsDigest)
      }
    }

    "return successful response for PUT request to monolithic blob upload path" in {
      val (blob, imageSlug) = Fixtures.await(for {
        user  <- UsersRepoFixture.create()
        image <- ImagesRepoFixture.create(user, imageName, ImageVisibilityKind.Private)
        blob  <- ImageBlobsRepoFixture.create(user.getId(), image.getId())
      } yield (blob, image.slug))

      Put(
        s"/v2/$imageSlug/blobs/uploads/${blob.getId}?digest=sha256:$bsDigest",
        HttpEntity(`application/octet-stream`, bs)
      ) ~> addCredentials(credentials) ~> route ~> check {
        status shouldEqual StatusCodes.Created
        responseEntity shouldEqual HttpEntity.Empty
        header[Location] should contain(Location(s"/v2/$imageSlug/blobs/sha256:$bsDigest"))
        header[`Docker-Content-Digest`] should contain(`Docker-Content-Digest`("sha256", bsDigest))
        header[`Docker-Distribution-Api-Version`] should contain(apiVersionHeader)
        val uuid = header[`Docker-Upload-Uuid`].map(h => UUID.fromString(h.value)).get

        val blob = await(ImageBlobsRepo.findById(uuid)).get
        blob.length shouldEqual bs.length
        blob.state shouldEqual ImageBlobState.Uploaded
        blob.sha256Digest shouldEqual Some(bsDigest)

        val file = BlobFileUtils.getBlobFilePath(bsDigest)
        file.length shouldEqual bs.length
        val fis        = new FileInputStream(file)
        val fileDigest = DigestUtils.sha256Hex(fis)
        fileDigest shouldEqual bsDigest
      }
    }

    "return successful response for PATCH requests to blob upload path" in {
      val (blob, imageSlug) = Fixtures.await(for {
        user  <- UsersRepoFixture.create()
        image <- ImagesRepoFixture.create(user, imageName, ImageVisibilityKind.Private)
        blob  <- ImageBlobsRepoFixture.create(user.getId(), image.getId())
      } yield (blob, image.slug))

      val blobPart1       = bs.take(bs.length / 2)
      val blobPart1Digest = DigestUtils.sha256Hex(blobPart1.toArray)

      Patch(
        s"/v2/$imageSlug/blobs/uploads/${blob.getId}",
        HttpEntity(`application/octet-stream`, blobPart1)
      ) ~> `Content-Range`(ContentRange(0L, blobPart1.length - 1L)) ~>
      addCredentials(credentials) ~> route ~> check {
        status shouldEqual StatusCodes.Accepted
        responseEntity shouldEqual HttpEntity.Empty
        header[Location] should contain(Location(s"/v2/$imageSlug/blobs/${blob.getId}"))
        header[`Docker-Upload-Uuid`] should contain(`Docker-Upload-Uuid`(blob.getId()))
        header[RangeCustom] should contain(RangeCustom(0L, blobPart1.length - 1L))
        header[`Docker-Distribution-Api-Version`] should contain(apiVersionHeader)

        val updatedBlob = await(ImageBlobsRepo.findById(blob.getId())).get
        updatedBlob.length shouldEqual blobPart1.length
        updatedBlob.state shouldEqual ImageBlobState.Uploading
        updatedBlob.sha256Digest shouldEqual Some(blobPart1Digest)

        val file = BlobFileUtils.getUploadFilePath(blob.getId())
        file.length shouldEqual blobPart1.length
        val fis        = new FileInputStream(file)
        val fileDigest = DigestUtils.sha256Hex(fis)
        fileDigest shouldEqual blobPart1Digest
      }

      val blobPart2 = bs.drop(blobPart1.length)

      Patch(
        s"/v2/$imageSlug/blobs/uploads/${blob.getId}",
        HttpEntity(`application/octet-stream`, blobPart2)
      ) ~> `Content-Range`(ContentRange(blobPart1.length.toLong, blobPart2.length - 1L)) ~>
      addCredentials(credentials) ~> route ~> check {
        status shouldEqual StatusCodes.Accepted
        responseEntity shouldEqual HttpEntity.Empty
        header[Location] should contain(Location(s"/v2/$imageSlug/blobs/${blob.getId}"))
        header[`Docker-Upload-Uuid`] should contain(`Docker-Upload-Uuid`(blob.getId()))
        header[RangeCustom] should contain(RangeCustom(0L, bs.length - 1L))
        header[`Docker-Distribution-Api-Version`] should contain(apiVersionHeader)

        val updatedBlob = await(ImageBlobsRepo.findById(blob.getId())).get
        updatedBlob.length shouldEqual bs.length
        updatedBlob.state shouldEqual ImageBlobState.Uploading
        updatedBlob.sha256Digest shouldEqual Some(bsDigest)

        val file = BlobFileUtils.getUploadFilePath(blob.getId())
        file.length shouldEqual bs.length
        val fis        = new FileInputStream(file)
        val fileDigest = DigestUtils.sha256Hex(fis)
        fileDigest shouldEqual bsDigest
      }
    }

    "return successful response for PUT request without final chunk to complete blob upload path" in {
      val (blob, imageSlug) = Fixtures.await(for {
        user  <- UsersRepoFixture.create()
        image <- ImagesRepoFixture.create(user, imageName, ImageVisibilityKind.Private)
        blob <- ImageBlobsRepoFixture.createAs(
                 user.getId(),
                 image.getId(),
                 bs,
                 ImageBlobState.Uploading
               )
        _ <- BlobFileUtils.uploadBlobChunk(blob.getId(), Source.single(bs))
      } yield (blob, image.slug))

      Put(
        s"/v2/$imageSlug/blobs/uploads/${blob.getId}?digest=sha256:$bsDigest",
        HttpEntity(`application/octet-stream`, ByteString.empty)
      ) ~> addCredentials(credentials) ~> route ~> check {
        status shouldEqual StatusCodes.Created
        responseAs[ByteString] shouldBe empty
        header[Location] should contain(Location(s"/v2/$imageSlug/blobs/sha256:$bsDigest"))
        header[`Docker-Upload-Uuid`] should contain(`Docker-Upload-Uuid`(blob.getId()))
        header[`Docker-Distribution-Api-Version`] should contain(apiVersionHeader)

        val b = await(ImageBlobsRepo.findById(blob.getId())).get
        b.length shouldEqual bs.length
        b.state shouldEqual ImageBlobState.Uploaded
        b.sha256Digest shouldEqual Some(bsDigest)
      }
    }

    "return successful response for PUT request with final chunk to complete blob upload path" in {
      val (blob, imageSlug) = Fixtures.await(for {
        user  <- UsersRepoFixture.create()
        image <- ImagesRepoFixture.create(user, imageName, ImageVisibilityKind.Private)
        blob <- ImageBlobsRepoFixture.createAs(
                 user.getId(),
                 image.getId(),
                 bs.take(1),
                 ImageBlobState.Uploading
               )
        _ <- BlobFileUtils.uploadBlobChunk(blob.getId(), Source.single(bs.take(1)))
      } yield (blob, image.slug))

      Put(
        s"/v2/$imageSlug/blobs/uploads/${blob.getId}?digest=sha256:$bsDigest",
        HttpEntity(`application/octet-stream`, bs.drop(1))
      ) ~> addCredentials(credentials) ~> route ~> check {
        status shouldEqual StatusCodes.Created
        responseAs[ByteString] shouldBe empty
        header[Location] should contain(Location(s"/v2/$imageSlug/blobs/sha256:$bsDigest"))
        header[`Docker-Upload-Uuid`] should contain(`Docker-Upload-Uuid`(blob.getId()))
        header[`Docker-Distribution-Api-Version`] should contain(apiVersionHeader)

        val b = await(ImageBlobsRepo.findById(blob.getId())).get
        b.length shouldEqual bs.length
        b.state shouldEqual ImageBlobState.Uploaded
        b.sha256Digest shouldEqual Some(bsDigest)
      }
    }

    "return successful response for PUT request without final chunk to complete blob path" in {
      val (blob, imageSlug) = Fixtures.await(for {
        user  <- UsersRepoFixture.create()
        image <- ImagesRepoFixture.create(user, imageName, ImageVisibilityKind.Private)
        blob <- ImageBlobsRepoFixture.createAs(
                 user.getId(),
                 image.getId(),
                 bs,
                 ImageBlobState.Uploading
               )
        _ <- BlobFileUtils.uploadBlobChunk(blob.getId(), Source.single(bs))
      } yield (blob, image.slug))

      Put(
        s"/v2/$imageSlug/blobs/${blob.getId}?digest=sha256:$bsDigest",
        HttpEntity(`application/octet-stream`, ByteString.empty)
      ) ~> addCredentials(credentials) ~> route ~> check {
        status shouldEqual StatusCodes.Created
        responseAs[ByteString] shouldBe empty
        header[Location] should contain(Location(s"/v2/$imageSlug/blobs/sha256:$bsDigest"))
        header[`Docker-Upload-Uuid`] should contain(`Docker-Upload-Uuid`(blob.getId()))
        header[`Docker-Distribution-Api-Version`] should contain(apiVersionHeader)

        val b = await(ImageBlobsRepo.findById(blob.getId())).get
        b.length shouldEqual bs.length
        b.state shouldEqual ImageBlobState.Uploaded
        b.sha256Digest shouldEqual Some(bsDigest)
      }
    }

    "return successful response for PUT request with final chunk to complete blob path" in {
      val (blob, imageSlug) = Fixtures.await(for {
        user  <- UsersRepoFixture.create()
        image <- ImagesRepoFixture.create(user, imageName, ImageVisibilityKind.Private)
        blob <- ImageBlobsRepoFixture.createAs(
                 user.getId(),
                 image.getId(),
                 bs.take(1),
                 ImageBlobState.Uploading
               )
        _ <- BlobFileUtils.uploadBlobChunk(blob.getId(), Source.single(bs.take(1)))
      } yield (blob, image.slug))

      Put(
        s"/v2/$imageSlug/blobs/${blob.getId}?digest=sha256:$bsDigest",
        HttpEntity(`application/octet-stream`, bs.drop(1))
      ) ~> addCredentials(credentials) ~> route ~> check {
        status shouldEqual StatusCodes.Created
        responseAs[ByteString] shouldBe empty
        header[Location] should contain(Location(s"/v2/$imageSlug/blobs/sha256:$bsDigest"))
        header[`Docker-Upload-Uuid`] should contain(`Docker-Upload-Uuid`(blob.getId()))
        header[`Docker-Distribution-Api-Version`] should contain(apiVersionHeader)

        val b = await(ImageBlobsRepo.findById(blob.getId())).get
        b.length shouldEqual bs.length
        b.state shouldEqual ImageBlobState.Uploaded
        b.sha256Digest shouldEqual Some(bsDigest)
      }
    }

    "return successful response for DELETE request to the blob upload path" in {
      val (blob, imageSlug) = Fixtures.await(for {
        user  <- UsersRepoFixture.create()
        image <- ImagesRepoFixture.create(user, imageName, ImageVisibilityKind.Private)
        blob <- ImageBlobsRepoFixture.createAs(
                 user.getId(),
                 image.getId(),
                 bs,
                 ImageBlobState.Uploading
               )
      } yield (blob, image.slug))

      Delete(s"/v2/$imageSlug/blobs/uploads/${blob.getId}") ~> addCredentials(credentials) ~> route ~> check {
        status shouldEqual StatusCodes.NoContent
        responseEntity shouldEqual HttpEntity.Empty

        val file = BlobFileUtils.getUploadFilePath(blob.getId())
        file.exists() should be(false)
      }
    }
  }
}
