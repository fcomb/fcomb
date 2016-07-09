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

import akka.http.scaladsl._, model._
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.model.headers._
import akka.http.scaladsl.testkit.ScalatestRouteTest
import akka.util.ByteString
import de.heikoseeberger.akkahttpcirce.CirceSupport._
import io.circe.generic.auto._
import io.fcomb.docker.distribution.server.headers._
import io.fcomb.docker.distribution.utils.BlobFileUtils
import io.fcomb.json.models.docker.distribution.CompatibleFormats._
import io.fcomb.models.docker.distribution._
import io.fcomb.tests._
import io.fcomb.tests.fixtures._
import io.fcomb.tests.fixtures.docker.distribution.{ImageBlobsRepoFixture, ImagesRepoFixture}
import org.apache.commons.codec.digest.DigestUtils
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{Matchers, WordSpec}
import io.fcomb.docker.distribution.server.Routes

class ImageBlobsHandlerSpec
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

  override def beforeEach(): Unit = {
    super.beforeEach()
    BlobFileUtils.getBlobFilePath(bsDigest).delete()
  }

  "The image blob handler" should {
    "return an info without content for HEAD request to the exist layer path" in {
      val imageSlug = Fixtures.await(for {
        user  <- UsersRepoFixture.create()
        image <- ImagesRepoFixture.create(user, imageName, ImageVisibilityKind.Private)
        _ <- ImageBlobsRepoFixture.createAs(
              user.getId(),
              image.getId(),
              bs,
              ImageBlobState.Uploaded
            )
      } yield image.slug)

      Head(s"/v2/$imageSlug/blobs/sha256:$bsDigest") ~> addCredentials(credentials) ~> route ~> check {
        status shouldEqual StatusCodes.OK
        responseAs[ByteString] shouldBe empty
        header[`Docker-Content-Digest`] should contain(`Docker-Content-Digest`("sha256", bsDigest))
        header[`Docker-Distribution-Api-Version`] should contain(apiVersionHeader)
        header[`Accept-Ranges`] should contain(`Accept-Ranges`(RangeUnits.Bytes))
        header[ETag] should contain(ETag(s"sha256:$bsDigest"))
        responseEntity.contentLengthOption shouldEqual Some(bs.length)
      }
    }

    "return a blob for GET request to the blob download path" in {
      val imageSlug = Fixtures.await(for {
        user  <- UsersRepoFixture.create()
        image <- ImagesRepoFixture.create(user, imageName, ImageVisibilityKind.Private)
        _ <- ImageBlobsRepoFixture.createAs(
              user.getId(),
              image.getId(),
              bs,
              ImageBlobState.Uploaded
            )
      } yield image.slug)

      Get(s"/v2/$imageSlug/blobs/sha256:$bsDigest") ~> addCredentials(credentials) ~> route ~> check {
        status shouldEqual StatusCodes.OK
        responseAs[ByteString] shouldEqual bs
        header[`Docker-Content-Digest`] should contain(`Docker-Content-Digest`("sha256", bsDigest))
        header[`Docker-Distribution-Api-Version`] should contain(apiVersionHeader)
        header[`Accept-Ranges`] should contain(`Accept-Ranges`(RangeUnits.Bytes))
        header[ETag] should contain(ETag(s"sha256:$bsDigest"))
        responseEntity.contentLengthOption shouldEqual Some(bs.length)
      }
    }

    "return a part of blob for GET request to the blob download path" in {
      val (blob, imageSlug) = Fixtures.await(for {
        user  <- UsersRepoFixture.create()
        image <- ImagesRepoFixture.create(user, imageName, ImageVisibilityKind.Private)
        blob <- ImageBlobsRepoFixture.createAs(
                 user.getId(),
                 image.getId(),
                 bs,
                 ImageBlobState.Uploaded
               )
      } yield (blob, image.slug))
      val offset = 5
      val limit  = 10

      Get(s"/v2/$imageSlug/blobs/sha256:$bsDigest") ~> Range(
        ByteRange(offset.toLong, limit.toLong)) ~> addCredentials(credentials) ~> route ~> check {
        status shouldEqual StatusCodes.PartialContent
        responseAs[ByteString] shouldEqual bs.drop(offset).take(limit - offset)
        header[`Content-Range`] should contain(
          `Content-Range`(
            ContentRange(
              offset.toLong,
              limit.toLong,
              blob.length
            )))
        header[`Docker-Content-Digest`] shouldBe empty
        header[`Docker-Distribution-Api-Version`] should contain(apiVersionHeader)
        header[`Accept-Ranges`] should contain(`Accept-Ranges`(RangeUnits.Bytes))
        header[ETag] shouldBe empty
        responseEntity.contentLengthOption should contain(limit - offset)
      }
    }

    "return not modified status for GET request to the blob download path" in {
      val imageSlug = Fixtures.await(for {
        user  <- UsersRepoFixture.create()
        image <- ImagesRepoFixture.create(user, imageName, ImageVisibilityKind.Private)
        _ <- ImageBlobsRepoFixture.createAs(
              user.getId(),
              image.getId(),
              bs,
              ImageBlobState.Uploaded
            )
      } yield image.slug)
      val headers = `If-None-Match`(EntityTag(s"sha256:$bsDigest"))

      Get(s"/v2/$imageSlug/blobs/sha256:$bsDigest") ~> headers ~> addCredentials(credentials) ~> route ~> check {
        status shouldEqual StatusCodes.NotModified
        responseAs[ByteString] shouldBe empty
      }
    }

    "return successful response for DELETE request to the blob path" in {
      val (blob, imageSlug) = Fixtures.await(for {
        user  <- UsersRepoFixture.create()
        image <- ImagesRepoFixture.create(user, imageName, ImageVisibilityKind.Private)
        blob <- ImageBlobsRepoFixture.createAs(
                 user.getId(),
                 image.getId(),
                 bs,
                 ImageBlobState.Uploaded
               )
      } yield (blob, image.slug))

      Delete(s"/v2/$imageSlug/blobs/sha256:${blob.digest.get}") ~> addCredentials(credentials) ~> route ~> check {
        status shouldEqual StatusCodes.NoContent
        responseEntity shouldEqual HttpEntity.Empty

        val file = BlobFileUtils.getUploadFilePath(blob.getId())
        file.exists() should be(false)
      }
    }
  }
}
