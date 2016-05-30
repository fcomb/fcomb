package io.fcomb.docker.distribution.server.utils

import akka.stream.Materializer
import akka.stream.scaladsl.{Source, FileIO}
import akka.util.ByteString
import io.fcomb.docker.distribution.server.services.ImageBlobPushProcessor
import io.fcomb.models.docker.distribution.{ImageBlob ⇒ MImageBlob, ImageManifest ⇒ MImageManifest}
import io.fcomb.utils.{Config, StringUtils}
import java.io.File
import java.security.MessageDigest
import java.util.UUID
import scala.concurrent.{ExecutionContext, Future, blocking}

object BlobFile {
  def getUploadFilePath(uuid: UUID): File =
    imageFilePath("uploads", uuid.toString)

  def createUploadFile(uuid: UUID)(
    implicit
    ec: ExecutionContext
  ): Future[File] = {
    val file = getUploadFilePath(uuid)
    Future(blocking {
      if (!file.getParentFile.exists) file.getParentFile.mkdirs()
      file
    })
  }

  def getBlobFilePath(digest: String): File =
    imageFilePath("blobs", digest)

  private def imageFilePath(path: String, name: String): File =
    new File(s"${Config.docker.distribution.imageStorage}/$path/${name.take(2)}/$name")

  def getFile(blob: MImageBlob): File = {
    blob.sha256Digest match {
      case Some(digest) if blob.isUploaded ⇒ getBlobFilePath(digest)
      case _                               ⇒ getUploadFilePath(blob.getId)
    }
  }

  def renameOrDelete(file: File, digest: String)(
    implicit
    ec: ExecutionContext
  ): Future[Unit] =
    Future(blocking {
      if (digest == MImageManifest.emptyTarSha256Digest) file.delete()
      else {
        val newFile = getBlobFilePath(digest)
        if (!newFile.exists()) {
          if (!newFile.getParentFile.exists()) newFile.getParentFile.mkdirs()
          file.renameTo(newFile)
        }
      }
    })

  def renameOrDelete(uuid: UUID, digest: String)(
    implicit
    ec: ExecutionContext
  ): Future[Unit] =
    renameOrDelete(getUploadFilePath(uuid), digest)

  def uploadBlob(uuid: UUID, data: Source[ByteString, Any])(
    implicit
    mat: Materializer
  ): Future[(Long, String)] = {
    import mat.executionContext
    val md = MessageDigest.getInstance("SHA-256")
    for {
      file ← createUploadFile(uuid)
      _ ← data
        .map { chunk ⇒
          md.update(chunk.toArray)
          chunk
        }
        .runWith(FileIO.toPath(file.toPath))
    } yield (file.length, StringUtils.hexify(md.digest))
  }

  def uploadBlobChunk(uuid: UUID, data: Source[ByteString, Any])(
    implicit
    mat: Materializer
  ): Future[(Long, String)] = {
    import mat.executionContext
    for {
      file ← createUploadFile(uuid)
      (length, digest) ← ImageBlobPushProcessor.uploadChunk(uuid, data, file)
    } yield (length, digest)
  }

  def destroyBlob(uuid: UUID)(implicit ec: ExecutionContext): Future[Unit] =
    Future(blocking(getUploadFilePath(uuid).delete))
}
