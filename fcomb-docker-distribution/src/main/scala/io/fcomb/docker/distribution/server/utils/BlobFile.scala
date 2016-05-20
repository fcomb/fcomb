package io.fcomb.docker.distribution.server.utils

import io.fcomb.models.docker.distribution.{ ImageBlob ⇒ MImageBlob }
import io.fcomb.utils.Config
import java.io.File
import java.util.UUID
import scala.concurrent.{ ExecutionContext, Future, blocking }

object BlobFile {
  def uploadFile(uuid: UUID): File =
    imageFile("uploads", uuid.toString)

  def createUploadFile(uuid: UUID)(
    implicit
    ec: ExecutionContext
  ): Future[File] = {
    val file = uploadFile(uuid)
    Future(
      blocking {
        if (!file.getParentFile.exists) file.getParentFile.mkdirs()
        file
      }
    )
  }

  def blobFile(digest: String): File =
    imageFile("blobs", digest)

  private def imageFile(path: String, name: String): File =
    new File(
      s"${Config.docker.distribution.imageStorage}/$path/${name.take(2)}/$name"
    )

  def getFile(blob: MImageBlob): File = {
    blob.sha256Digest match {
      case Some(digest) if blob.isUploaded ⇒ blobFile(digest)
      case _                               ⇒ uploadFile(blob.getId)
    }
  }

  def uploadToBlob(file: File, digest: String)(
    implicit
    ec: ExecutionContext
  ): Future[Unit] =
    Future(
      blocking {
        val newFile = blobFile(digest)
        if (!newFile.exists()) {
          if (!newFile.getParentFile.exists()) newFile.getParentFile.mkdirs()
          file.renameTo(newFile)
        }
      }
    )
}
