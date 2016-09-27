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

package io.fcomb.docker.distribution.server.api

import akka.http.scaladsl.model.ContentTypes.`application/json`
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server._
import akka.util.ByteString
import cats.data.Xor
import io.fcomb.docker.distribution.manifest.{
  SchemaV1 => SchemaV1Manifest,
  SchemaV2 => SchemaV2Manifest
}
import io.fcomb.docker.distribution.server.ContentTypes.{
  `application/vnd.docker.distribution.manifest.v1+prettyjws`,
  `application/vnd.docker.distribution.manifest.v2+json`
}
import io.fcomb.docker.distribution.server.AuthenticationDirectives._
import io.fcomb.docker.distribution.server.CommonDirectives._
import io.fcomb.docker.distribution.server.ImageDirectives._
import io.fcomb.docker.distribution.server.MediaTypes
import io.fcomb.docker.distribution.server.headers._
import io.fcomb.json.models.docker.distribution.CompatibleFormats._
import io.fcomb.json.models.docker.distribution.Formats._
import io.fcomb.models.acl.Action
import io.fcomb.models.docker.distribution._
import io.fcomb.models.errors.docker.distribution.DistributionError
import io.fcomb.persist.docker.distribution.{ImageManifestsRepo, ImagesRepo}
import io.fcomb.server.CirceSupport._
import io.fcomb.server.CommonDirectives._
import scala.collection.immutable

object ImagesHandler {
  def getManifest(imageName: String, reference: Reference)(implicit req: HttpRequest) =
    tryAuthenticateUserBasic { userOpt =>
      extractMaterializer { implicit mat =>
        optionalHeaderValueByType[Accept]() { acceptOpt =>
          imageByNameWithReadAcl(imageName, userOpt.flatMap(_.id)) { image =>
            import mat.executionContext
            onSuccess(ImageManifestsRepo.findByImageIdAndReference(image.getId(), reference)) {
              case Some(im) =>
                val headers = immutable.Seq(
                  ETag(im.sha256Digest),
                  `Docker-Content-Digest`("sha256", im.digest)
                )
                respondWithHeaders(headers) {
                  im.schemaVersion match {
                    case 1 =>
                      completeSchemaV1Manifest(SchemaV1Manifest.addSignature(im.schemaV1JsonBlob))
                    case _ =>
                      reference match {
                        case Reference.Tag(tag) if !acceptOpt.exists(acceptIsAManifestV2) =>
                          completeSchemaV1Manifest(
                            SchemaV1Manifest.addTagAndSignature(im.schemaV1JsonBlob, tag))
                        case _ =>
                          val e = ByteString(im.getSchemaV2JsonBlob)
                          complete(
                            HttpEntity(`application/vnd.docker.distribution.manifest.v2+json`, e))
                      }
                  }
                }
              case _ => completeError(DistributionError.manifestUnknown)
            }
          }
        }
      }
    }

  private def completeSchemaV1Manifest(m: Xor[String, String]): Route =
    m match {
      case Xor.Right(jb) =>
        val e = ByteString(jb)
        complete(HttpEntity(`application/vnd.docker.distribution.manifest.v1+prettyjws`, e))
      case Xor.Left(e) => completeError(DistributionError.unknown(e))
    }

  private def acceptIsAManifestV2(header: Accept) =
    header.mediaRanges.exists { r =>
      r.matches(MediaTypes.`application/vnd.docker.distribution.manifest.v2+json`) &&
      r != MediaRanges.`*/*`
    }

  def uploadManifest(imageName: String, reference: Reference)(implicit req: HttpRequest) =
    authenticateUserBasic { user =>
      extractMaterializer { implicit mat =>
        val userId = user.getId()
        imageByNameWithAcl(imageName, userId, Action.Write) { image =>
          import mat.executionContext
          entity(as[ByteString]) { rawManifestBs =>
            respondWithContentType(`application/json`) {
              entity(as[SchemaManifest]) { manifest =>
                val rawManifest = rawManifestBs.utf8String
                val res = manifest match {
                  case m: SchemaV1.Manifest =>
                    SchemaV1Manifest.upsertAsImageManifest(image,
                                                           reference,
                                                           m,
                                                           rawManifest,
                                                           userId)
                  case m: SchemaV2.Manifest =>
                    SchemaV2Manifest.upsertAsImageManifest(image,
                                                           reference,
                                                           m,
                                                           rawManifest,
                                                           userId)
                }
                onSuccess(res) {
                  case Xor.Right(digest) =>
                    val headers = immutable.Seq(
                      `Docker-Content-Digest`("sha256", digest),
                      Location(s"/v2/$imageName/manifests/sha256:$digest")
                    )
                    respondWithHeaders(headers) {
                      complete((StatusCodes.Created, HttpEntity.Empty))
                    }
                  case Xor.Left(e) => completeError(e)
                }
              }
            }
          }
        }
      }
    }

  private def respondWithContentType(contentType: ContentType): Directive0 =
    mapRequest { req =>
      req.copy(
        entity = req.entity.withContentType(contentType),
        headers = req.headers.filterNot(_.isInstanceOf[Accept])
      )
    }

  def destroyManifest(imageName: String, reference: Reference) =
    authenticateUserBasic { user =>
      extractMaterializer { implicit mat =>
        import mat.executionContext
        imageByNameWithAcl(imageName, user.getId(), Action.Manage) { image =>
          reference match {
            case Reference.Digest(digest) =>
              onSuccess(ImageManifestsRepo.destroy(image.getId(), digest)) { res =>
                if (res) completeAccepted()
                else completeError(DistributionError.manifestUnknown)
              }
            case _ => completeError(DistributionError.manifestInvalid())
          }
        }
      }
    }

  def tags(imageName: String) =
    tryAuthenticateUserBasic { userOpt =>
      parameters('n.as[Int].?, 'last.?) { (n, last) =>
        extractExecutionContext { implicit ec =>
          imageByNameWithReadAcl(imageName, userOpt.flatMap(_.id)) { image =>
            onSuccess(ImageManifestsRepo.findTagsByImageId(image.getId(), n, last)) {
              (tags, limit, hasNext) =>
                val headers = if (hasNext) {
                  val uri = Uri(s"/v2/$imageName/tags/list?n=$limit&last=${tags.last}")
                  immutable.Seq(Link(uri, LinkParams.next))
                } else Nil
                respondWithHeaders(headers) {
                  complete(ImageTagsResponse(imageName, tags))
                }
            }
          }
        }
      }
    }

  def catalog =
    authenticateUserBasic { user =>
      parameters('n.as[Int].?, 'last.?) { (n, last) =>
        extractExecutionContext { implicit ec =>
          onSuccess(ImagesRepo.findRepositoriesByUserId(user.getId(), n, last)) {
            (repositories, limit, hasNext) =>
              val headers = if (hasNext) {
                val uri = Uri(s"/v2/_catalog?n=$limit&last=${repositories.last}")
                immutable.Seq(Link(uri, LinkParams.next))
              } else Nil
              respondWithHeaders(headers) {
                complete(DistributionImageCatalog(repositories))
              }
          }
        }
      }
    }
}
