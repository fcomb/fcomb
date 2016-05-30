package io.fcomb.docker.distribution.server.api

import akka.http.scaladsl.marshalling.Marshal
import akka.http.scaladsl.model.ContentTypes.{`application/octet-stream`, `application/json`}
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers._
import akka.http.scaladsl.server._
import akka.http.scaladsl.util.FastFuture, FastFuture._
import akka.stream.Materializer
import akka.stream.scaladsl._
import akka.util.ByteString
import cats.data.{Validated, Xor}
import cats.syntax.eq._
import io.fcomb.docker.distribution.manifest.{SchemaV1 ⇒ SchemaV1Manifest, SchemaV2 ⇒ SchemaV2Manifest}
import io.fcomb.docker.distribution.server.api.ContentTypes.{`application/vnd.docker.distribution.manifest.v1+json`, `application/vnd.docker.distribution.manifest.v1+prettyjws`, `application/vnd.docker.distribution.manifest.v2+json`}
import io.fcomb.docker.distribution.server.api.headers._
import io.fcomb.docker.distribution.server.services.ImageBlobPushProcessor
import io.fcomb.docker.distribution.server.utils.BlobFile
import io.fcomb.models.docker.distribution.{Image ⇒ MImage, ImageManifest ⇒ MImageManifest, _}
import io.fcomb.models.errors.docker.distribution.{DistributionError, DistributionErrorResponse}
import io.fcomb.models.{User ⇒ MUser}
import io.fcomb.persist.docker.distribution.{ImageBlob ⇒ PImageBlob, Image ⇒ PImage, ImageManifest ⇒ PImageManifest}
import io.fcomb.utils.{Config, StringUtils}, Config.docker.distribution.realm
import java.io.File
import java.security.MessageDigest
import java.util.UUID
import scala.collection.immutable
import scala.compat.java8.OptionConverters._
import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future, blocking}
import scala.util.{Right, Left}

import AuthDirectives._

// trait ValidationDirectives {

// }

// object ValidationDirectives extends ValidationDirectives

// import ValidationDirectives._

// TODO: move blob upload methods into BlobUploadService
object ImageService {
  import akka.http.scaladsl.server.Directives._

  import io.fcomb.models.errors._
  import io.fcomb.json.docker.distribution.Formats._
  import de.heikoseeberger.akkahttpcirce.CirceSupport._
  import io.circe.generic.auto._

  @inline
  private def completeWithStatus(status: StatusCode): Route =
    complete(HttpResponse(status))

  @inline
  private def completeNotFound(): Route =
    completeWithStatus(StatusCodes.NotFound)

  def createBlob(imageName: String)(implicit req: HttpRequest): Route =
    authenticateUserBasic(realm) { user ⇒
      parameters('mount.?, 'from.?, 'digest.?) {
        (mountOpt, fromOpt, digestOpt) ⇒
          extractExecutionContext { implicit ec ⇒
            (mountOpt, fromOpt, digestOpt) match {
              case (Some(mount), Some(from), _) ⇒
                mountImageBlob(user, imageName, mount, from)
              case (_, _, Some(digest)) ⇒
                createImageBlob(user, imageName, digest)
              case _ ⇒
                createImageBlobUpload(user, imageName)
            }
          }
      }
    }

  private def createImageBlobUpload(user: MUser, imageName: String)(
    implicit
    ec:  ExecutionContext,
    req: HttpRequest
  ): Route = {
    val contentType = req.entity.contentType.mediaType.value
    onSuccess(PImageBlob.createByImageName(imageName, user.getId, contentType)) {
      case Validated.Valid(blob) ⇒
        val uuid = blob.getId
        val headers = immutable.Seq(
          Location(s"/v2/$imageName/blobs/uploads/$uuid"),
          `Docker-Upload-Uuid`(uuid),
          rangeHeader(0L, 0L)
        )
        respondWithHeaders(headers) {
          completeWithStatus(StatusCodes.Accepted)
        }
      case Validated.Invalid(e) ⇒
        complete(StatusCodes.Accepted, FailureResponse.fromExceptions(e))
    }
  }

  private def mountImageBlob(user: MUser, imageName: String, digest: String, from: String)(
    implicit
    ec:  ExecutionContext,
    req: HttpRequest
  ): Route = {
    imageByNameWithAcl(from, user) { fromImage ⇒
      onSuccess(PImageBlob.mount(fromImage.getId, imageName, Reference.getDigest(digest), user.getId)) {
        case Some(blob) ⇒
          val sha256Digest = blob.sha256Digest.get
          val headers = immutable.Seq(
            Location(s"/v2/$imageName/blobs/sha256:$sha256Digest"),
            `Docker-Content-Digest`("sha256", sha256Digest)
          )
          respondWithHeaders(headers) {
            completeWithStatus(StatusCodes.Created)
          }
        case None ⇒ createImageBlobUpload(user, imageName)
      }
    }
  }

  private def createImageBlob(user: MUser, imageName: String, digest: String)(
    implicit
    req: HttpRequest
  ): Route =
    extractMaterializer { implicit mat ⇒
      import mat.executionContext
      val source = req.entity.dataBytes
      val contentType = req.entity.contentType.mediaType.value
      onSuccess(for {
        Validated.Valid(blob) ← PImageBlob.createByImageName(imageName, user.getId, contentType)
        file ← BlobFile.createUploadFile(blob.getId)
        sha256Digest ← writeFile(source, file)
        fileLength ← Future(blocking(file.length))
        _ ← PImageBlob.completeUploadOrDelete(blob.getId, blob.imageId, fileLength, sha256Digest)
      } yield (blob, sha256Digest, file)) {
        case (blob, sha256Digest, file) ⇒
          if (Reference.getDigest(digest) == sha256Digest) {
            onSuccess(BlobFile.renameOrDelete(file, sha256Digest)) {
              val headers = immutable.Seq(
                Location(s"/v2/$imageName/blobs/sha256:$sha256Digest"),
                `Docker-Upload-Uuid`(blob.getId),
                `Docker-Content-Digest`("sha256", sha256Digest)
              )
              respondWithHeaders(headers) {
                completeWithStatus(StatusCodes.Created)
              }
            }
          }
          else {
            onSuccess(for {
              _ ← Future(blocking(file.delete()))
              _ ← PImageBlob.destroy(blob.getId)
            } yield ()) {
              complete(
                StatusCodes.BadRequest,
                DistributionErrorResponse.from(DistributionError.DigestInvalid())
              )
            }
          }
      }
    }

  final case class ImageTagsResponse(
    name: String,
    tags: Seq[String]
  )

  def tags(imageName: String) =
    authenticateUserBasic(realm) { user ⇒
      parameters('n.as[Int].?, 'last.?) { (n, last) ⇒
        extractExecutionContext { implicit ec ⇒
          imageByNameWithAcl(imageName, user) { image ⇒
            onSuccess(PImageManifest.findTagsByImageId(image.getId, n, last)) {
              (tags, limit, hasNext) ⇒
                val headers =
                  if (hasNext) {
                    val uri = Uri(
                      s"/v2/$imageName/tags/list?n=$limit&last=${tags.last}"
                    )
                    immutable.Seq(Link(uri, LinkParams.next))
                  }
                  else Nil
                respondWithHeaders(headers) {
                  complete(ImageTagsResponse(imageName, tags))
                }
            }
          }
        }
      }
    }

  private def imageByNameWithAcl(
    imageName: String, user: MUser
  ): Directive1[MImage] = {
    extractExecutionContext.flatMap { implicit ec ⇒
      onSuccess(PImage.findByImageAndUserId(imageName, user.getId)).flatMap {
        case Some(user) ⇒ provide(user)
        case None       ⇒ complete(HttpResponse(StatusCodes.NotFound)) // TODO
      }
    }
  }

  def uploadBlob(imageName: String, uuid: UUID)(implicit req: HttpRequest) =
    authenticateUserBasic(realm) { user ⇒
      parameters('digest) { dgst ⇒
        extractMaterializer { implicit mat ⇒
          imageByNameWithAcl(imageName, user) { image ⇒
            import mat.executionContext
            onSuccess(PImageBlob.findByImageIdAndUuid(image.getId, uuid)) {
              case Some(blob) if blob.state === ImageBlobState.Created ⇒
                val digest = Reference.getDigest(dgst)
                complete {
                  BlobFile.uploadBlob(uuid, req.entity.dataBytes).flatMap {
                    case (length, sha256Digest) ⇒
                      if (sha256Digest == digest) {
                        val headers = immutable.Seq(
                          Location(s"/v2/$imageName/blobs/sha256:$digest"),
                          `Docker-Upload-Uuid`(uuid),
                          `Docker-Content-Digest`("sha256", digest)
                        )
                        for {
                          _ ← BlobFile.renameOrDelete(uuid, digest)
                          _ ← PImageBlob.completeUploadOrDelete(uuid, blob.imageId, length, sha256Digest)
                        } yield HttpResponse(StatusCodes.Created, headers)
                      }
                      else {
                        val e = DistributionErrorResponse.from(DistributionError.DigestInvalid())
                        Marshal(StatusCodes.BadRequest → e).to[HttpResponse]
                      }
                  }
                }
              case None ⇒
                complete(
                  StatusCodes.NotFound,
                  DistributionErrorResponse.from(DistributionError.BlobUploadInvalid())
                )
            }
          }
        }
      }
    }

  def uploadBlobChunk(imageName: String, uuid: UUID)(
    implicit
    req: HttpRequest
  ) =
    authenticateUserBasic(realm) { user ⇒
      extractMaterializer { implicit mat ⇒
        optionalHeaderValueByType[`Content-Range`]() { rangeOpt ⇒
          imageByNameWithAcl(imageName, user) { image ⇒
            import mat.executionContext
            onSuccess(PImageBlob.findByImageIdAndUuid(image.getId, uuid)) {
              case Some(blob) if blob.state === ImageBlobState.Created || blob.state === ImageBlobState.Uploading ⇒
                val (rangeFrom, rangeTo) = rangeOpt match {
                  case Some(r) ⇒
                    val cr = r.contentRange
                    (cr.getSatisfiableFirst.asScala, cr.getSatisfiableLast.asScala)
                  case None ⇒ (None, None)
                }
                val isRangeValid = (rangeFrom, rangeTo) match {
                  case (Some(from), Some(to)) ⇒ from >= to
                  case _                      ⇒ true
                }
                if (isRangeValid) {
                  complete(
                    StatusCodes.BadRequest,
                    DistributionErrorResponse.from(DistributionError.Unknown("Range is invalid"))
                  )
                }
                else if (rangeFrom.exists(_ != blob.length)) {
                  complete(
                    StatusCodes.BadRequest,
                    DistributionErrorResponse.from(DistributionError.Unknown("Range start not satisfy a blob file length"))
                  )
                }
                else {
                  val data = (rangeFrom, rangeTo) match {
                    case (Some(from), Some(to)) ⇒
                      req.entity.dataBytes.take(to - from + 1)
                    case _ ⇒ req.entity.dataBytes
                  }
                  val totalLengthFut =
                    for {
                      (length, digest) ← BlobFile.uploadBlobChunk(uuid, data)
                      totalLength = blob.length + length
                      _ ← PImageBlob.updateState(uuid, totalLength, digest, ImageBlobState.Uploading)
                    } yield totalLength
                  onSuccess(totalLengthFut) { totalLength ⇒
                    val headers = immutable.Seq(
                      Location(s"/v2/$imageName/blobs/$uuid"),
                      `Docker-Upload-Uuid`(uuid),
                      rangeHeader(0L, totalLength)
                    )
                    respondWithHeaders(headers) {
                      complete(StatusCodes.Accepted, HttpEntity.Empty)
                    }
                  }
                }
              case _ ⇒
                complete(
                  StatusCodes.NotFound,
                  DistributionErrorResponse.from(DistributionError.BlobUploadInvalid())
                )
            }
          }
        }
      }
    }

  def uploadComplete(imageName: String, uuid: UUID)(
    implicit
    req: HttpRequest
  ) =
    authenticateUserBasic(realm) { user ⇒
      extractMaterializer { implicit mat ⇒
        imageByNameWithAcl(imageName, user) { image ⇒
          import mat.executionContext
          val queryDigest = Reference.getDigest(req.uri.query().get("digest").get)
          val headers = immutable.Seq(
            Location(s"/v2/$imageName/blobs/sha256:$queryDigest"),
            `Docker-Content-Digest`("sha256", queryDigest)
          )
          onSuccess(PImageBlob.findByImageIdAndUuid(image.getId, uuid)) {
            case Some(blob) ⇒
              if (blob.state === ImageBlobState.Uploaded)
                complete(HttpResponse(StatusCodes.Created, headers))
              else {
                assert(blob.state === ImageBlobState.Uploading) // TODO
                onSuccess(for {
                  file ← BlobFile.createUploadFile(blob.getId)
                  (length, digest) ← ImageBlobPushProcessor.uploadChunk(
                    uuid, req.entity.dataBytes, file
                  )
                  Xor.Right(_) ← ImageBlobPushProcessor.stop(blob.getId)
                } yield (file, length, digest)) {
                  case (file, length, digest) ⇒
                    if (digest == queryDigest) {
                      onSuccess(for {
                        _ ← BlobFile.renameOrDelete(file, digest)
                        _ ← PImageBlob.completeUploadOrDelete(
                          uuid, blob.imageId, blob.length + length, digest
                        )
                      } yield ()) {
                        respondWithHeaders(headers) {
                          complete(StatusCodes.Created, HttpEntity.Empty)
                        }
                      }
                    }
                    else {
                      onSuccess(for {
                        _ ← Future(blocking(file.delete))
                        _ ← PImageBlob.destroy(uuid)
                      } yield ()) {
                        complete(StatusCodes.BadRequest, DistributionErrorResponse.from(DistributionError.DigestInvalid()))
                      }
                    }
                }
              }
            case None ⇒ completeNotFound() // TODO
          }
        }
      }
    }

  def destroyBlobUpload(imageName: String, uuid: UUID) =
    authenticateUserBasic(realm) { user ⇒
      extractMaterializer { implicit mat ⇒
        imageByNameWithAcl(imageName, user) { image ⇒
          import mat.executionContext
          onSuccess(PImageBlob.findByImageIdAndUuid(image.getId, uuid)) {
            case Some(blob) if blob.state === ImageBlobState.Created ||
              blob.state === ImageBlobState.Uploading ⇒
              val file = BlobFile.getUploadFilePath(blob.getId)
              complete(for {
                _ ← Future(blocking(file.delete))
                _ ← PImageBlob.destroy(uuid)
              } yield HttpResponse(StatusCodes.NoContent))
            case None ⇒ completeNotFound() // TODO
          }
        }
      }
    }

  def destroyBlob(imageName: String, digest: String) =
    authenticateUserBasic(realm) { user ⇒
      extractMaterializer { implicit mat ⇒
        imageByNameWithAcl(imageName, user) { image ⇒
          import mat.executionContext
          val sha256Digest = Reference.getDigest(digest)
          onSuccess(PImageBlob.findByImageIdAndDigest(image.getId, sha256Digest)) {
            case Some(blob) if blob.state === ImageBlobState.Uploaded ⇒
              val file = BlobFile.getUploadFilePath(blob.getId)
              complete(for {
                _ ← Future(blocking(file.delete)) // TODO: only if file exists once
                _ ← PImageBlob.destroy(blob.getId) // TODO: destroy only unlinked blob
              } yield HttpResponse(StatusCodes.NoContent))
            case None ⇒ completeNotFound() // TODO
          }
        }
      }
    }

  private def contentType(contentType: String) =
    ContentType.parse(contentType) match {
      case Right(res) ⇒ res
      case Left(_)    ⇒ `application/octet-stream`
    }

  def showBlob(imageName: String, digest: String) =
    authenticateUserBasic(realm) { user ⇒
      extractMaterializer { implicit mat ⇒
        imageByNameWithAcl(imageName, user) { image ⇒
          import mat.executionContext
          val sha256Digest = Reference.getDigest(digest)
          onSuccess(PImageBlob.findByImageIdAndDigest(image.getId, sha256Digest)) {
            case Some(blob) ⇒
              complete(
                HttpResponse(
                  StatusCodes.OK,
                  immutable.Seq(
                    `Docker-Content-Digest`("sha256", sha256Digest),
                    ETag(digest),
                    `Accept-Ranges`(RangeUnits.Bytes), // TODO: spec
                    cacheHeader
                  ),
                  HttpEntity(
                    contentType(blob.contentType),
                    blob.length,
                    Source.empty
                  )
                )
              )
            case None ⇒ completeNotFound() // TODO
          }
        }
      }
    }

  private val emptyTarSource =
    Source.single(ByteString(MImageManifest.emptyTar))

  def downloadBlob(imageName: String, digest: String)(
    implicit
    req: HttpRequest
  ) =
    authenticateUserBasic(realm) { user ⇒
      extractMaterializer { implicit mat ⇒
        imageByNameWithAcl(imageName, user) { image ⇒
          import mat.executionContext
          val sha256Digest = Reference.getDigest(digest)
          onSuccess(PImageBlob.findByImageIdAndDigest(image.getId, sha256Digest)) {
            case Some(blob) ⇒
              val source =
                if (digest == MImageManifest.emptyTarSha256Digest) emptyTarSource
                else FileIO.fromPath(BlobFile.getFile(blob).toPath)
              val ct = contentType(blob.contentType)
              req.header[Range] match {
                case Some(range) ⇒
                  val r = range.ranges.head // TODO
                  val offset: Long = r.getOffset.asScala
                    .orElse(r.getSliceFirst.asScala)
                    .getOrElse(0L)
                  val limit: Long = r.getSliceLast.asScala
                    .map(_ - offset)
                    .getOrElse(blob.length)
                  complete(HttpResponse(
                    StatusCodes.PartialContent,
                    immutable.Seq(`Content-Range`(ContentRange(offset, limit, blob.length))),
                    HttpEntity(ct, blob.length, source.drop(offset).take(limit))
                  ))
                case None ⇒
                  complete(HttpResponse(
                    StatusCodes.OK,
                    immutable.Seq(`Docker-Content-Digest`("sha256", sha256Digest)),
                    HttpEntity(ct, blob.length, source)
                  ))
              }
            case None ⇒ completeNotFound() // TODO
          }
        }
      }
    }

  private val v1ContentTypes = Set[ContentType](
    `application/vnd.docker.distribution.manifest.v1+json`,
    `application/vnd.docker.distribution.manifest.v1+prettyjws`
  )

  def getManifest(imageName: String, reference: Reference)(
    implicit
    req: HttpRequest
  ) =
    authenticateUserBasic(realm) { user ⇒
      extractMaterializer { implicit mat ⇒
        imageByNameWithAcl(imageName, user) { image ⇒
          import mat.executionContext
          onSuccess(PImageManifest.findByImageIdAndReference(image.getId, reference)) {
            case Some(im) ⇒
              val (ct: ContentType, manifest: String) = im.schemaVersion match {
                case 1 ⇒
                  (ContentTypes.`application/vnd.docker.distribution.manifest.v1+prettyjws`, im.schemaV1JsonBlob)
                case 2 ⇒ reference match {
                  case Reference.Tag(tag) if !v1ContentTypes.contains(req.entity.contentType) ⇒
                    val jb = SchemaV1Manifest.addTagAndSignature(im.schemaV1JsonBlob, tag)
                    (`application/vnd.docker.distribution.manifest.v1+prettyjws`, jb)
                  case _ ⇒
                    (`application/vnd.docker.distribution.manifest.v2+json`, im.getSchemaV2JsonBlob)
                }
              }
              complete(HttpEntity(ct, ByteString(manifest)))
            case None ⇒ completeNotFound() // TODO
          }
        }
      }
    }

  def uploadManifest(imageName: String, reference: Reference)(
    implicit
    mat: Materializer,
    req: HttpRequest
  ) =
    authenticateUserBasic(realm) { user ⇒
      extractMaterializer { implicit mat ⇒
        imageByNameWithAcl(imageName, user) { image ⇒
          import mat.executionContext
          entity(as[ByteString]) { rawManifestBs ⇒
            respondWithContentType(`application/json`) {
              entity(as[SchemaManifest]) { manifest ⇒
                val rawManifest = rawManifestBs.utf8String
                val res = manifest match {
                  case m: SchemaV1.Manifest ⇒
                    SchemaV1Manifest.upsertAsImageManifest(image, reference, m, rawManifest)
                  case m: SchemaV2.Manifest ⇒
                    SchemaV2Manifest.upsertAsImageManifest(image, reference, m, rawManifest)
                }
                onSuccess(res) {
                  case Xor.Right(sha256Digest) ⇒
                    respondWithHeaders(`Docker-Content-Digest`("sha256", sha256Digest)) {
                      complete(StatusCodes.Created, HttpEntity.Empty)
                    }
                  case Xor.Left(e) ⇒
                    complete(StatusCodes.BadRequest, DistributionErrorResponse.from(e))
                }
              }
            }
          }
        }
      }
    }

  private def respondWithContentType(contentType: ContentType): Directive0 =
    mapRequest { req ⇒
      req.copy(
        entity = req.entity.withContentType(contentType),
        headers = req.headers.filterNot(_.isInstanceOf[Accept])
      )
    }

  def destroyManifest(imageName: String, reference: Reference) =
    authenticateUserBasic(realm) { user ⇒
      extractMaterializer { implicit mat ⇒
        imageByNameWithAcl(imageName, user) { image ⇒
          reference match {
            case Reference.Digest(digest) ⇒
              onSuccess(PImageManifest.destroy(image.getId, digest)) {
                // TODO: not found
                case _ ⇒ complete(StatusCodes.NoContent, HttpEntity.Empty)
              }
            case _ ⇒ ??? // TODO
          }
        }
      }
    }

  final case class DistributionImageCatalog(
    repositories: Seq[String]
  )

  def catalog =
    authenticateUserBasic(realm) { user ⇒
      parameters('n.as[Int].?, 'last.?) { (n, last) ⇒
        extractExecutionContext { implicit ec ⇒
          onSuccess(PImage.findRepositoriesByUserId(user.getId, n, last)) {
            (repositories, limit, hasNext) ⇒
              val headers =
                if (hasNext) {
                  val uri = Uri(s"/v2/_catalog?n=$limit&last=${repositories.last}")
                  immutable.Seq(Link(uri, LinkParams.next))
                }
                else Nil
              respondWithHeaders(headers) {
                complete(DistributionImageCatalog(repositories))
              }
          }
        }
      }
    }

  private val cacheHeader = `Cache-Control`(
    CacheDirectives.`max-age`(365.days.toSeconds)
  )

  private def rangeHeader(from: Long, length: Long) = {
    val to =
      if (from < length) length - 1
      else from
    RangeCustom(from, to)
  }

  private def writeFile(source: Source[ByteString, Any], file: File)(
    implicit
    ec:  ExecutionContext,
    mat: Materializer
  ) = {
    val md = MessageDigest.getInstance("SHA-256")
    source.map { chunk ⇒
      md.update(chunk.toArray)
      chunk
    }.runWith(FileIO.toPath(file.toPath)).fast.map { _ ⇒
      StringUtils.hexify(md.digest)
    }
  }
}
