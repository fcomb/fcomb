package io.fcomb.docker.distribution.server.api

import akka.http.scaladsl.marshalling.{Marshal, ToEntityMarshaller}
import akka.http.scaladsl.model.ContentTypes.`application/octet-stream`
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers._
import akka.http.scaladsl.server._
import akka.http.scaladsl.util.FastFuture, FastFuture._
import akka.stream.Materializer
import akka.stream.scaladsl._
import akka.util.ByteString
import cats.data.{Validated, Xor}
import cats.syntax.eq._
import io.circe.Encoder
import io.fcomb.docker.distribution.server.api.ContentTypes.`application/vnd.docker.distribution.manifest.v2+json`
import io.fcomb.docker.distribution.server.api.headers._
import io.fcomb.docker.distribution.server.services.ImageBlobPushProcessor
import io.fcomb.docker.distribution.server.utils.BlobFile
import io.fcomb.models.docker.distribution.{Image ⇒ MImage, _}
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

trait ResponseDirectives {
  def response[T](status: StatusCode, entity: T)(
    implicit
    ec: ExecutionContext, m: ToEntityMarshaller[T]
  ): Future[HttpResponse] =
    response(status, Nil, entity)

  def response[T](status: StatusCode, headers: immutable.Seq[HttpHeader] = Nil, entity: T)(
    implicit
    ec: ExecutionContext, m: ToEntityMarshaller[T]
  ): Future[HttpResponse] =
    Marshal(entity).to[ResponseEntity].fast.map(HttpResponse(status, headers, _))
}

object ResponseDirectives extends ResponseDirectives

import ResponseDirectives._

// TODO: move blob upload methods into BlobUploadService
object ImageService {
  import akka.http.scaladsl.server.Directives._

  import io.fcomb.models.errors._
  import io.fcomb.json.docker.distribution.Formats._
  import de.heikoseeberger.akkahttpcirce.CirceSupport._

  import io.circe.generic.auto._

  implicit val encodeErrorKind = new Encoder[ErrorKind.ErrorKind] {
    def apply(kind: ErrorKind.ErrorKind) =
      Encoder[String].apply(kind.toString)
  }

  def createBlobUpload(imageName: String)(implicit req: HttpRequest): Route =
    authenticateUserBasic(realm) { user ⇒
      extractExecutionContext { implicit ec ⇒
        // val mount = ctx.requestContext.request.uri.query().get("mount")
        // val from = ctx.requestContext.request.uri.query().get("from")
        val contentType = req.entity.contentType.mediaType.value
        onSuccess(PImageBlob.createByImageName(imageName, user.getId, contentType)) {
          case Validated.Valid(blob) ⇒
            val uuid = blob.getId
            val headers = immutable.Seq(
              Location(s"/v2/$imageName/blobs/uploads/$uuid"),
              `Docker-Upload-Uuid`(uuid),
              rangeHeader(0L, 0L)
            )
            complete(HttpResponse(StatusCodes.Accepted, headers))
          case Validated.Invalid(e) ⇒
            complete(response(StatusCodes.BadRequest, FailureResponse.fromExceptions(e)))
        }
      }
    }

  def createBlob(name: String, digest: String)(implicit req: HttpRequest) =
    authenticateUserBasic(realm) { user ⇒
      extractMaterializer { implicit mat ⇒
        import mat.executionContext
        val source = req.entity.dataBytes
        val contentType = req.entity.contentType.mediaType.value
        onSuccess((for {
          Validated.Valid(blob) ← PImageBlob.createByImageName(name, user.getId, contentType)
          file ← BlobFile.createUploadFile(blob.getId)
          sha256Digest ← writeFile(source, file)
          fileLength ← Future(blocking(file.length))
          _ ← PImageBlob.completeUpload(blob.getId, fileLength, sha256Digest)
        } yield (blob, sha256Digest, file)).flatMap {
          case (blob, sha256Digest, file) ⇒
            if (parseDigest(digest) == sha256Digest) {
              BlobFile.uploadToBlob(file, sha256Digest).fast.map { _ ⇒
                HttpResponse(StatusCodes.Created, immutable.Seq(
                  Location(s"/v2/$name/blobs/sha256:$sha256Digest"),
                  `Docker-Upload-Uuid`(blob.getId),
                  `Docker-Content-Digest`("sha256", sha256Digest)
                ))
              }
            }
            else {
              for {
                _ ← Future(blocking(file.delete()))
                _ ← PImageBlob.destroy(blob.getId)
                res ← response(
                  StatusCodes.BadRequest,
                  DistributionErrorResponse.from(DistributionError.DigestInvalid())
                )
              } yield res
            }
        })(complete(_))
      }
    }

  final case class ImageTagsResponse(
    name: String,
    tags: Seq[String]
  )

  def tags(imageName: String) =
    authenticateUserBasic(realm) { user ⇒
      parameters('n.as[Option[Int]], 'last.as[Option[String]]) { (n, last) ⇒
        extractExecutionContext { implicit ec ⇒
          imageByNameWithAcl(imageName, user) { image ⇒
            onSuccess(PImageManifest.findTagsByImageId(image.getId, n, last)) { (tags, limit, hasNext) ⇒
              val headers =
                if (hasNext) {
                  val uri = Uri(s"/v2/$imageName/tags/list?n=$limit&last=${tags.last}")
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

  private def imageByNameWithAcl(imageName: String, user: MUser): Directive1[MImage] = {
    extractExecutionContext.flatMap { implicit ec ⇒
      onSuccess(PImage.findByImageAndUserId(imageName, user.getId)).flatMap {
        case Some(user) ⇒ provide(user)
        case None       ⇒ complete(StatusCodes.NotFound) // TODO
      }
    }
  }

  def uploadBlob(imageName: String, uuid: UUID)(implicit req: HttpRequest) =
    authenticateUserBasic(realm) { user ⇒
      extractMaterializer { implicit mat ⇒
        imageByNameWithAcl(imageName, user) { image ⇒
          import mat.executionContext
          onSuccess(PImageBlob.findByImageIdAndUuid(image.getId, uuid)) {
            case Some(blob) ⇒
              assert(blob.state === ImageBlobState.Created) // TODO: monolithic cannot be uploaded through chunk;move into FSM
              val md = MessageDigest.getInstance("SHA-256")
              complete(for {
                file ← BlobFile.createUploadFile(blob.getId)
                _ ← req.entity.dataBytes.map { chunk ⇒
                  md.update(chunk.toArray)
                  chunk
                }.runWith(akka.stream.scaladsl.FileIO.toFile(file))
                // TODO: check file for 0 size
                digest = StringUtils.hexify(md.digest)
                // TODO: check digest for unique
                _ ← PImageBlob.completeUpload(uuid, file.length, digest)
              } yield HttpResponse(StatusCodes.Created, immutable.Seq(
                Location(s"/v2/$imageName/blobs/sha256:$digest"),
                `Docker-Upload-Uuid`(uuid),
                `Docker-Content-Digest`("sha256", digest)
              )))
            case None ⇒ complete(StatusCodes.NotFound) // TODO
          }
        }
      }
    }

  def uploadBlobChunk(imageName: String, uuid: UUID)(implicit req: HttpRequest) =
    authenticateUserBasic(realm) { user ⇒
      extractMaterializer { implicit mat ⇒
        imageByNameWithAcl(imageName, user) { image ⇒
          import mat.executionContext
          onSuccess(PImageBlob.findByImageIdAndUuid(image.getId, uuid)) {
            case Some(blob) ⇒
              assert(blob.state === ImageBlobState.Created || blob.state === ImageBlobState.Uploading) // TODO: move into FSM
              // TODO: support content range and validate if it exists (Chunked and Streamed upload)
              // val contentRange = ctx.requestContext.request.header[`Content-Range`].get
              // val rangeFrom = contentRange.contentRange.getSatisfiableFirst.asScala.get
              // val rangeTo = contentRange.contentRange.getSatisfiableLast.asScala.get
              // assert(rangeFrom == blob.length)
              // assert(rangeTo >= rangeFrom)
              complete(for {
                file ← BlobFile.createUploadFile(blob.getId)
                (length, digest) ← ImageBlobPushProcessor.uploadChunk(
                  blob.getId,
                  req.entity.dataBytes, // .take(rangeTo - rangeFrom + 1),
                  file
                )
                // TODO: check file for 0 size
                _ ← PImageBlob.updateState(uuid, blob.length + length, digest, ImageBlobState.Uploading)
              } yield HttpResponse(StatusCodes.Accepted, immutable.Seq(
                Location(s"/v2/$imageName/blobs/$uuid"),
                `Docker-Upload-Uuid`(uuid),
                rangeHeader(0L, file.length)
              )))
            case None ⇒ complete(StatusCodes.NotFound) // TODO
          }
        }
      }
    }

  def uploadComplete(imageName: String, uuid: UUID)(implicit req: HttpRequest) =
    authenticateUserBasic(realm) { user ⇒
      extractMaterializer { implicit mat ⇒
        imageByNameWithAcl(imageName, user) { image ⇒
          import mat.executionContext
          val queryDigest = parseDigest(req.uri.query().get("digest").get)
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
                complete((for {
                  file ← BlobFile.createUploadFile(blob.getId)
                  (length, digest) ← ImageBlobPushProcessor.uploadChunk(uuid, req.entity.dataBytes, file)
                  Xor.Right(_) ← ImageBlobPushProcessor.stop(blob.getId)
                } yield (file, length, digest)).flatMap {
                  case (file, length, digest) ⇒
                    if (digest == queryDigest) {
                      for {
                        _ ← BlobFile.uploadToBlob(file, digest)
                        _ ← PImageBlob.completeUpload(uuid, blob.length + length, digest)
                      } yield HttpResponse(StatusCodes.Created, headers)
                    }
                    else {
                      for {
                        _ ← Future(blocking(file.delete))
                        _ ← PImageBlob.destroy(uuid)
                        res ← response(
                          StatusCodes.BadRequest,
                          DistributionErrorResponse.from(DistributionError.DigestInvalid())
                        )
                      } yield res
                    }
                })
              }
            case None ⇒ complete(StatusCodes.NotFound) // TODO
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
            case Some(blob) if blob.state === ImageBlobState.Created || blob.state === ImageBlobState.Uploading ⇒
              val file = BlobFile.uploadFile(blob.getId)
              complete(for {
                _ ← Future(blocking(file.delete))
                _ ← PImageBlob.destroy(uuid)
              } yield HttpResponse(StatusCodes.NoContent))
            case None ⇒ complete(StatusCodes.NotFound) // TODO
          }
        }
      }
    }

  def destroyBlob(imageName: String, digest: String) =
    authenticateUserBasic(realm) { user ⇒
      extractMaterializer { implicit mat ⇒
        imageByNameWithAcl(imageName, user) { image ⇒
          import mat.executionContext
          val sha256Digest = parseDigest(digest)
          onSuccess(PImageBlob.findByImageIdAndDigest(image.getId, sha256Digest)) {
            case Some(blob) if blob.state === ImageBlobState.Uploaded ⇒
              val file = BlobFile.uploadFile(blob.getId)
              complete(for {
                _ ← Future(blocking(file.delete)) // TODO: only if file exists once
                _ ← PImageBlob.destroy(blob.getId) // TODO: destroy only unlinked blob
              } yield HttpResponse(StatusCodes.NoContent))
            case None ⇒ complete(StatusCodes.NotFound) // TODO
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
          val sha256Digest = parseDigest(digest)
          onSuccess(PImageBlob.findByImageIdAndDigest(image.getId, sha256Digest)) {
            case Some(blob) ⇒
              complete(HttpResponse(
                StatusCodes.OK,
                immutable.Seq(
                  `Docker-Content-Digest`("sha256", sha256Digest),
                  ETag(digest),
                  `Accept-Ranges`(RangeUnits.Bytes), // TODO: spec
                  cacheHeader
                ),
                HttpEntity(contentType(blob.contentType), blob.length, Source.empty)
              ))
            case None ⇒ complete(StatusCodes.NotFound) // TODO
          }
        }
      }
    }

  def downloadBlob(imageName: String, digest: String)(implicit req: HttpRequest) =
    authenticateUserBasic(realm) { user ⇒
      extractMaterializer { implicit mat ⇒
        imageByNameWithAcl(imageName, user) { image ⇒
          import mat.executionContext
          val sha256Digest = parseDigest(digest)
          onSuccess(PImageBlob.findByImageIdAndDigest(image.getId, sha256Digest)) {
            case Some(blob) ⇒
              val source = FileIO.fromFile(BlobFile.getFile(blob))
              val ct = contentType(blob.contentType)
              req.header[Range] match {
                case Some(range) ⇒
                  val r = range.ranges.head // TODO
                  val offset: Long = r.getOffset.asScala.orElse(r.getSliceFirst.asScala).getOrElse(0L)
                  val limit: Long = r.getSliceLast.asScala.map(_ - offset).getOrElse(blob.length)
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
            case None ⇒ complete(StatusCodes.NotFound) // TODO
          }
        }
      }
    }

  def getManifest(imageName: String, reference: String) =
    authenticateUserBasic(realm) { user ⇒
      extractMaterializer { implicit mat ⇒
        imageByNameWithAcl(imageName, user) { image ⇒
          import mat.executionContext
          onSuccess(PImageManifest.findByImageIdAndReferenceAsManifestV2(image.getId, reference)) {
            case Some(manifest) ⇒ complete(manifest)
            case None           ⇒ complete(StatusCodes.NotFound) // TODO
          }
        }
      }
    }

  def uploadManifest(imageName: String, reference: String)(implicit req: HttpRequest) =
    authenticateUserBasic(realm) { user ⇒
      extractMaterializer { implicit mat ⇒
        imageByNameWithAcl(imageName, user) { image ⇒
          import mat.executionContext
          entity(as[ByteString]) { rawManifest ⇒
            entity(as[Manifest]) { manifest ⇒
              assert(req.entity.contentType == `application/vnd.docker.distribution.manifest.v2+json`) // TODO
              onSuccess(PImageManifest.upsertByRequest(imageName, reference, manifest, rawManifest.utf8String)) {
                case Validated.Valid(m) ⇒
                  complete(HttpResponse(StatusCodes.Created, immutable.Seq(
                    `Docker-Content-Digest`("sha256", m.sha256Digest)
                  )))
                case Validated.Invalid(e) ⇒
                  complete(response(StatusCodes.BadRequest, FailureResponse.fromExceptions(e)))
              }
            }
          }
        }
      }
    }

  def destroyManifest(imageName: String, digest: String) =
    authenticateUserBasic(realm) { user ⇒
      extractMaterializer { implicit mat ⇒
        imageByNameWithAcl(imageName, user) { image ⇒
          onSuccess(PImageManifest.destroy(image.getId, parseDigest(digest))) {
            case _ ⇒ complete(HttpResponse(StatusCodes.NoContent))
          }
        }
      }
    }

  def catalog =
    authenticateUserBasic(realm) { user ⇒
      extractExecutionContext { implicit ec ⇒
        complete(PImage.repositoriesByUserId(user.getId))
      }
    }

  private val cacheHeader =
    `Cache-Control`(CacheDirectives.`max-age`(365.days.toSeconds))

  private def parseDigest(digest: String) = digest.split(':').last

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
    }.runWith(FileIO.toFile(file)).fast.map { _ ⇒
      StringUtils.hexify(md.digest)
    }
  }
}
