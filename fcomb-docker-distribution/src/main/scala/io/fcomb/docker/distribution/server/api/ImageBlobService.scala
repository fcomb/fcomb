package io.fcomb.docker.distribution.server.api

import akka.http.scaladsl.marshalling.Marshal
import akka.http.scaladsl.model.ContentTypes.`application/octet-stream`
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers._
import akka.http.scaladsl.util.FastFuture, FastFuture._
import akka.stream.scaladsl._
import akka.util.ByteString
import cats.data.Xor
import io.fcomb.docker.distribution.server.api.headers._
import io.fcomb.docker.distribution.server.utils.BlobFile
import io.fcomb.models.docker.distribution.{ImageManifest ⇒ MImageManifest, _}
import io.fcomb.models.errors.docker.distribution.{DistributionError, DistributionErrorResponse}
import io.fcomb.persist.docker.distribution.{ImageBlob ⇒ PImageBlob}
import scala.collection.immutable
import scala.compat.java8.OptionConverters._
import scala.concurrent.duration._
import scala.util.{Right, Left}
import akka.http.scaladsl.server.Directives._
import io.fcomb.json.docker.distribution.Formats._
import de.heikoseeberger.akkahttpcirce.CirceSupport._
import io.circe.generic.auto._

import AuthenticationDirectives._
import ImageDirectives._
import CommonDirectives._

object ImageBlobService {
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

  private val cacheHeader = `Cache-Control`(
    CacheDirectives.`max-age`(365.days.toSeconds)
  )

  private def contentType(contentType: String) =
    ContentType.parse(contentType) match {
      case Right(res) ⇒ res
      case Left(_)    ⇒ `application/octet-stream`
    }
}
