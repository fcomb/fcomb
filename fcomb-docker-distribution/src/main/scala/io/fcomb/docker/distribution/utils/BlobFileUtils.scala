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

package io.fcomb.docker.distribution.utils

import akka.stream.Materializer
import akka.stream.scaladsl.{Sink, Source}
import akka.util.ByteString
import io.fcomb.docker.distribution.services.ImageBlobPushProcessor
import io.fcomb.models.docker.distribution.ImageBlob
import io.fcomb.utils.Config
import java.io.File
import java.util.UUID

import scala.concurrent.{blocking, ExecutionContext, Future}

object BlobFileUtils {
  private lazy val storageAdapter = StorageAdapter.getInstance()

  def source(file: File)(implicit ec: ExecutionContext): Source[ByteString, Any] =
    storageAdapter.source(file)

  def sink(file: File)(implicit ec: ExecutionContext): Sink[ByteString, Future[Any]] =
    storageAdapter.sink(file)

  def getUploadFilePath(uuid: UUID): File =
    imageFilePath("uploads", uuid.toString)

  def getBlobFilePath(digest: String): File =
    imageFilePath("blobs", digest)

  private def imageFilePath(path: String, name: String): File =
    new File(s"${Config.docker.distribution.imageStorage}/$path/${name.take(2)}/${name.drop(2)}")

  def getFile(blob: ImageBlob): File =
    blob.digest match {
      case Some(digest) if blob.isUploaded => getBlobFilePath(digest)
      case _                               => getUploadFilePath(blob.getId())
    }

  def rename(uuid: UUID, digest: String)(implicit ec: ExecutionContext): Future[Unit] =
    Future(blocking {
      storageAdapter.rename(getUploadFilePath(uuid), getBlobFilePath(digest))
      ()
    })

  def uploadBlobChunk(uuid: UUID, data: Source[ByteString, Any])(
      implicit mat: Materializer
  ): Future[(Long, String)] = {
    import mat.executionContext
    ImageBlobPushProcessor.uploadChunk(uuid, data, getUploadFilePath(uuid))
  }

  def streamBlob(digest: String, offset: Long, limit: Long)(
      implicit ec: ExecutionContext): Source[ByteString, Any] =
    storageAdapter.sourcePart(getBlobFilePath(digest), offset, limit)

  def destroyUploadBlob(uuid: UUID)(implicit ec: ExecutionContext): Future[Unit] =
    Future(blocking {
      storageAdapter.destroy(getUploadFilePath(uuid))
      ()
    })

  def destroyBlob(digest: String)(implicit ec: ExecutionContext): Future[Unit] =
    Future(blocking {
      storageAdapter.destroy(getBlobFilePath(digest))
      ()
    })
}
