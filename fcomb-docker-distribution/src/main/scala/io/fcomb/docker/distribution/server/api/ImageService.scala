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
import cats.data.Xor
import io.fcomb.docker.distribution.manifest.{SchemaV1 ⇒ SchemaV1Manifest, SchemaV2 ⇒ SchemaV2Manifest}
import io.fcomb.docker.distribution.server.api.ContentTypes.{`application/vnd.docker.distribution.manifest.v1+json`, `application/vnd.docker.distribution.manifest.v1+prettyjws`, `application/vnd.docker.distribution.manifest.v2+json`}
import io.fcomb.docker.distribution.server.api.headers._
import io.fcomb.docker.distribution.server.utils.BlobFile
import io.fcomb.models.docker.distribution.{Image ⇒ MImage, ImageManifest ⇒ MImageManifest, _}
import io.fcomb.models.errors.docker.distribution.{DistributionError, DistributionErrorResponse}
import io.fcomb.models.{User ⇒ MUser}
import io.fcomb.persist.docker.distribution.{ImageBlob ⇒ PImageBlob, Image ⇒ PImage, ImageManifest ⇒ PImageManifest}
import scala.collection.immutable
import scala.compat.java8.OptionConverters._
import scala.concurrent.duration._
import scala.util.{Right, Left}
import akka.http.scaladsl.server.Directives._
import io.fcomb.json.docker.distribution.Formats._
import de.heikoseeberger.akkahttpcirce.CirceSupport._
import io.circe.generic.auto._

import AuthenticationDirectives._

trait ImageDirectives {
  def imageByNameWithAcl(imageName: String, user: MUser): Directive1[MImage] = {
    extractExecutionContext.flatMap { implicit ec ⇒
      onSuccess(PImage.findByImageAndUserId(imageName, user.getId)).flatMap {
        case Some(user) ⇒ provide(user)
        case None ⇒
          complete(
            StatusCodes.NotFound,
            DistributionErrorResponse.from(DistributionError.NameUnknown())
          )
      }
    }
  }
}

object ImageDirectives extends ImageDirectives

import ImageDirectives._
import CommonDirectives._

// TODO: move blob upload methods into BlobUploadService
object ImageService {
  def tags(imageName: String) =
    authenticationUserBasic { user ⇒
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

  def destroyBlob(imageName: String, digest: String) =
    authenticationUserBasic { user ⇒
      extractMaterializer { implicit mat ⇒
        imageByNameWithAcl(imageName, user) { image ⇒
          import mat.executionContext
          val sha256Digest = Reference.getDigest(digest)
          onSuccess(PImageBlob.findByImageIdAndDigest(image.getId, sha256Digest)) {
            case Some(blob) if blob.isUploaded ⇒
              val res =
                PImageBlob.tryDestroy(blob.getId).flatMap {
                  case Xor.Right(_) ⇒
                    PImageBlob.existByDigest(digest)
                      .flatMap {
                        case false ⇒ BlobFile.destroyBlob(blob.getId)
                        case true  ⇒ FastFuture.successful(())
                      }
                      .fast
                      .map(_ ⇒ HttpResponse(StatusCodes.NoContent))
                  case Xor.Left(msg) ⇒
                    val e = DistributionErrorResponse.from(DistributionError.Unknown(msg))
                    Marshal(StatusCodes.InternalServerError → e).to[HttpResponse]
                }
              complete(res)
            case _ ⇒
              complete(
                StatusCodes.NotFound,
                DistributionErrorResponse.from(DistributionError.BlobUploadInvalid())
              )
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
    authenticationUserBasic { user ⇒
      extractMaterializer { implicit mat ⇒
        imageByNameWithAcl(imageName, user) { image ⇒
          import mat.executionContext
          val sha256Digest = Reference.getDigest(digest)
          onSuccess(PImageBlob.findByImageIdAndDigest(image.getId, sha256Digest)) {
            case Some(blob) if blob.isUploaded ⇒
              complete(
                HttpResponse(
                  StatusCodes.OK,
                  immutable.Seq(
                    `Docker-Content-Digest`("sha256", sha256Digest),
                    ETag(digest),
                    `Accept-Ranges`(RangeUnits.Bytes),
                    cacheHeader
                  ),
                  HttpEntity(contentType(blob.contentType), blob.length, Source.empty)
                )
              )
            case _ ⇒ completeNotFound()
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
    authenticationUserBasic { user ⇒
      extractMaterializer { implicit mat ⇒
        imageByNameWithAcl(imageName, user) { image ⇒
          import mat.executionContext
          val sha256Digest = Reference.getDigest(digest)
          onSuccess(PImageBlob.findByImageIdAndDigest(image.getId, sha256Digest)) {
            case Some(blob) if blob.isUploaded ⇒
              val source =
                if (digest == MImageManifest.emptyTarSha256Digest) emptyTarSource
                else FileIO.fromPath(BlobFile.getFile(blob).toPath)
              val ct = contentType(blob.contentType)
              optionalHeaderValueByType[Range]() {
                case Some(Range(_, range +: _)) ⇒
                  val offset: Long = range.getOffset.asScala
                    .orElse(range.getSliceFirst.asScala)
                    .getOrElse(0L)
                  val limit: Long = range.getSliceLast.asScala
                    .map(_ - offset)
                    .getOrElse(blob.length)
                  val headers = immutable.Seq(
                    `Content-Range`(ContentRange(offset, limit, blob.length))
                  )
                  val chunk = source.drop(offset).take(limit)
                  complete(HttpResponse(
                    StatusCodes.PartialContent,
                    headers,
                    HttpEntity(ct, blob.length, chunk)
                  ))
                case _ ⇒
                  optionalHeaderValueByType[`If-None-Match`]() {
                    case Some(`If-None-Match`(EntityTagRange.Default(EntityTag(digest, _) +: _))) ⇒
                      completeWithStatus(StatusCodes.NotModified)
                    case _ ⇒
                      val headers = immutable.Seq(
                        ETag(digest),
                        `Docker-Content-Digest`("sha256", sha256Digest),
                        cacheHeader
                      )
                      complete(HttpResponse(
                        StatusCodes.OK,
                        headers,
                        HttpEntity(ct, blob.length, source)
                      ))
                  }
              }
            case _ ⇒ completeNotFound()
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
    authenticationUserBasic { user ⇒
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
            case _ ⇒
              complete(
                StatusCodes.NotFound,
                DistributionErrorResponse.from(DistributionError.ManifestUnknown())
              )
          }
        }
      }
    }

  def uploadManifest(imageName: String, reference: Reference)(
    implicit
    mat: Materializer,
    req: HttpRequest
  ) =
    authenticationUserBasic { user ⇒
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
    authenticationUserBasic { user ⇒
      extractMaterializer { implicit mat ⇒
        import mat.executionContext
        imageByNameWithAcl(imageName, user) { image ⇒
          reference match {
            case Reference.Digest(digest) ⇒
              onSuccess(PImageManifest.destroy(image.getId, digest)) { res ⇒
                if (res) complete(StatusCodes.Accepted, HttpEntity.Empty)
                else complete(
                  StatusCodes.NotFound,
                  DistributionErrorResponse.from(DistributionError.ManifestUnknown())
                )
              }
            case _ ⇒
              complete(
                StatusCodes.NotFound,
                DistributionErrorResponse.from(DistributionError.ManifestInvalid())
              )
          }
        }
      }
    }

  final case class DistributionImageCatalog(
    repositories: Seq[String]
  )

  def catalog =
    authenticationUserBasic { user ⇒
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
}
