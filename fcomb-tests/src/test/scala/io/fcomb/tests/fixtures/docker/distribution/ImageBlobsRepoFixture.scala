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

package io.fcomb.tests.fixtures.docker.distribution

import java.util.UUID
import java.time.ZonedDateTime
import akka.util.ByteString
import cats.data.Validated
import io.fcomb.models.docker.distribution.{ImageBlob, ImageBlobState}
import io.fcomb.persist.docker.distribution.ImageBlobsRepo
import io.fcomb.docker.distribution.utils.BlobFileUtils
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import org.apache.commons.codec.digest.DigestUtils
import akka.stream.scaladsl.{Source, FileIO}
import akka.stream.Materializer

object ImageBlobsRepoFixture {
  def create(userId: Int, imageId: Int): Future[ImageBlob] = {
    val id = UUID.randomUUID()
    val blob = ImageBlob(
      id = Some(id),
      state = ImageBlobState.Created,
      imageId = imageId,
      sha256Digest = None,
      contentType = "application/octet-stream",
      length = 0L,
      createdAt = ZonedDateTime.now(),
      uploadedAt = None
    )
    (for {
      Validated.Valid(res) <- ImageBlobsRepo.create(blob)
    } yield res)
  }

  def createAs(
      userId: Int,
      imageId: Int,
      bs: ByteString,
      state: ImageBlobState,
      digestOpt: Option[String] = None
  )(implicit mat: Materializer): Future[ImageBlob] = {
    val id     = UUID.randomUUID()
    val digest = digestOpt.getOrElse(DigestUtils.sha256Hex(bs.toArray))
    val blob = ImageBlob(
      id = Some(id),
      state = state,
      imageId = imageId,
      sha256Digest = Some(digest),
      length = bs.length.toLong,
      contentType = "application/octet-stream",
      createdAt = ZonedDateTime.now(),
      uploadedAt = None
    )
    for {
      Validated.Valid(im) <- ImageBlobsRepo.create(blob)
      file = BlobFileUtils.getFile(blob)
      _    = file.getParentFile.mkdirs()
      _ <- Source.single(bs).runWith(FileIO.toPath(file.toPath))
    } yield im
  }
}
