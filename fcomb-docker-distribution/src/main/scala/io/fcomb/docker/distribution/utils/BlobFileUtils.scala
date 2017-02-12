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

package io.fcomb.docker.distribution.utils

import akka.stream.Materializer
import akka.stream.scaladsl.{Source, StreamConverters}
import akka.util.ByteString
import com.google.common.io.ByteStreams
import io.fcomb.docker.distribution.services.ImageBlobPushProcessor
import io.fcomb.models.docker.distribution.ImageBlob
import io.fcomb.utils.Config
import java.io.File
import java.nio.file.Files
import java.util.UUID
import scala.concurrent.{blocking, ExecutionContext, Future}

object BlobFileUtils {
  def getUploadFilePath(uuid: UUID): File =
    imageFilePath("uploads", uuid.toString)

  def createUploadFile(uuid: UUID)(implicit ec: ExecutionContext): Future[File] = {
    val file = getUploadFilePath(uuid)
    Future(blocking {
      if (!file.getParentFile.exists) file.getParentFile.mkdirs()
      file
    })
  }

  def getBlobFilePath(digest: String): File =
    imageFilePath("blobs", digest)

  private def imageFilePath(path: String, name: String): File =
    new File(s"${Config.docker.distribution.imageStorage}/$path/${name.take(2)}/${name.drop(2)}")

  def getFile(blob: ImageBlob): File =
    blob.digest match {
      case Some(digest) if blob.isUploaded => getBlobFilePath(digest)
      case _                               => getUploadFilePath(blob.getId())
    }

  def rename(file: File, digest: String)(implicit ec: ExecutionContext): Future[Unit] =
    Future(blocking {
      val newFile = getBlobFilePath(digest)
      if (!newFile.exists()) {
        if (!newFile.getParentFile.exists()) newFile.getParentFile.mkdirs()
        file.renameTo(newFile)
      }
      ()
    })

  def rename(uuid: UUID, digest: String)(implicit ec: ExecutionContext): Future[Unit] =
    rename(getUploadFilePath(uuid), digest)

  def uploadBlobChunk(uuid: UUID, data: Source[ByteString, Any])(
      implicit mat: Materializer
  ): Future[(Long, String)] = {
    import mat.executionContext
    for {
      file             <- createUploadFile(uuid)
      (length, digest) <- ImageBlobPushProcessor.uploadChunk(uuid, data, file)
    } yield (length, digest)
  }

  def streamBlob(digest: String, offset: Long, limit: Long): Source[ByteString, Any] = {
    val file = getBlobFilePath(digest)
    val is   = Files.newInputStream(file.toPath)
    if (offset > 0) is.skip(offset)
    val bs = ByteStreams.limit(is, limit)
    StreamConverters.fromInputStream(() => bs, 8192)
  }

  def destroyUploadBlob(uuid: UUID)(implicit ec: ExecutionContext): Future[Unit] =
    Future(blocking {
      getUploadFilePath(uuid).delete
      ()
    })

  def destroyBlob(digest: String)(implicit ec: ExecutionContext): Future[Unit] =
    Future(blocking {
      getBlobFilePath(digest).delete
      ()
    })
}
