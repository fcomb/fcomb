/*
 * Copyright 2017 fcomb. <https://fcomb.io>
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

import akka.http.scaladsl.model.headers._
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.model._
import akka.util.ByteString
import io.fcomb.docker.distribution.server.Api
import io.fcomb.docker.distribution.server.headers._
import io.fcomb.docker.distribution.utils.BlobFileUtils
import io.fcomb.json.models.docker.distribution.CompatibleFormats._
import io.fcomb.models.docker.distribution._
import io.fcomb.tests.fixtures.docker.distribution.{ImageBlobsFixture, ImagesFixture}
import io.fcomb.tests.fixtures._
import io.fcomb.tests._
import org.apache.commons.codec.digest.DigestUtils

final class ImageBlobsHandlerSpec extends ApiHandlerSpec {
  val route            = Api.routes()
  val imageName        = "test-image_2016"
  val bs               = ByteString(getFixture("docker/distribution/blob"))
  val bsDigest         = DigestUtils.sha256Hex(bs.toArray)
  val credentials      = BasicHttpCredentials(UsersFixture.username, UsersFixture.password)
  val apiVersionHeader = `Docker-Distribution-Api-Version`("2.0")

  override def beforeEach(): Unit = {
    super.beforeEach()
    BlobFileUtils.getBlobFilePath(bsDigest).delete()
    ()
  }

  "The image blob handler" should {
    "return an info without content for HEAD request to the exist layer path" in {
      val imageSlug = (for {
        user  <- UsersFixture.create()
        image <- ImagesFixture.create(user, imageName, ImageVisibilityKind.Private)
        _ <- ImageBlobsFixture.createAs(
          user.getId(),
          image.getId(),
          bs,
          ImageBlobState.Uploaded
        )
      } yield image.slug) futureValue

      Head(s"/v2/$imageSlug/blobs/sha256:$bsDigest") ~> addCredentials(credentials) ~> route ~> check {
        status shouldEqual StatusCodes.OK
        checkBlobHeaders()
        responseAs[ByteString] shouldBe empty
        responseEntity.contentLengthOption shouldEqual Some(bs.length)
      }
    }

    "return a blob for GET request to the blob download path" in {
      val imageSlug = (for {
        user  <- UsersFixture.create()
        image <- ImagesFixture.create(user, imageName, ImageVisibilityKind.Private)
        _ <- ImageBlobsFixture.createAs(
          user.getId(),
          image.getId(),
          bs,
          ImageBlobState.Uploaded
        )
      } yield image.slug).futureValue

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
      val (blob, imageSlug) = (for {
        user  <- UsersFixture.create()
        image <- ImagesFixture.create(user, imageName, ImageVisibilityKind.Private)
        blob <- ImageBlobsFixture.createAs(
          user.getId(),
          image.getId(),
          bs,
          ImageBlobState.Uploaded
        )
      } yield (blob, image.slug)).futureValue
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
      val imageSlug = (for {
        user  <- UsersFixture.create()
        image <- ImagesFixture.create(user, imageName, ImageVisibilityKind.Private)
        _ <- ImageBlobsFixture.createAs(
          user.getId(),
          image.getId(),
          bs,
          ImageBlobState.Uploaded
        )
      } yield image.slug).futureValue
      val headers = `If-None-Match`(EntityTag(s"sha256:$bsDigest"))

      Get(s"/v2/$imageSlug/blobs/sha256:$bsDigest") ~> headers ~> addCredentials(credentials) ~> route ~> check {
        status shouldEqual StatusCodes.NotModified
        responseAs[ByteString] shouldBe empty
      }
    }

    "return successful response for DELETE request to the blob path" in {
      val (blob, imageSlug) = (for {
        user  <- UsersFixture.create()
        image <- ImagesFixture.create(user, imageName, ImageVisibilityKind.Private)
        blob <- ImageBlobsFixture.createAs(
          user.getId(),
          image.getId(),
          bs,
          ImageBlobState.Uploaded
        )
      } yield (blob, image.slug)).futureValue

      Delete(s"/v2/$imageSlug/blobs/sha256:${blob.digest.get}") ~> addCredentials(credentials) ~> route ~> check {
        status shouldEqual StatusCodes.NoContent
        responseEntity shouldEqual HttpEntity.Empty

        val file = BlobFileUtils.getUploadFilePath(blob.getId())
        file.exists() should be(false)
      }
    }

    "return an info for uploaded blob (with uploading blobs with the same digest)" in {
      val imageSlug = (for {
        user  <- UsersFixture.create()
        image <- ImagesFixture.create(user, imageName, ImageVisibilityKind.Private)
        _     <- ImageBlobsFixture.createAs(user.getId(), image.getId(), bs, ImageBlobState.Uploading)
        _     <- ImageBlobsFixture.createAs(user.getId(), image.getId(), bs, ImageBlobState.Uploaded)
        _     <- ImageBlobsFixture.createAs(user.getId(), image.getId(), bs, ImageBlobState.Uploading)

      } yield image.slug).futureValue

      Head(s"/v2/$imageSlug/blobs/sha256:$bsDigest") ~> addCredentials(credentials) ~> route ~> check {
        status shouldEqual StatusCodes.OK
        checkBlobHeaders()
      }

      Get(s"/v2/$imageSlug/blobs/sha256:$bsDigest") ~> addCredentials(credentials) ~> route ~> check {
        status shouldEqual StatusCodes.OK
        checkBlobHeaders()
      }
    }
  }

  private def checkBlobHeaders() = {
    header[`Docker-Content-Digest`] should contain(`Docker-Content-Digest`("sha256", bsDigest))
    header[`Docker-Distribution-Api-Version`] should contain(apiVersionHeader)
    header[`Accept-Ranges`] should contain(`Accept-Ranges`(RangeUnits.Bytes))
    header[ETag] should contain(ETag(s"sha256:$bsDigest"))
  }
}
