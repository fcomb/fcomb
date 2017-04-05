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

package io.fcomb.application.server

import akka.actor.ActorSystem
import akka.event.LoggingAdapter
import akka.http.scaladsl.model.headers.{`Content-Encoding`, EntityTag, HttpEncodings}
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directive0
import akka.http.scaladsl.server.directives.FileAndResourceDirectives.ResourceFile
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.directives._
import akka.http.scaladsl.server.EncodingNegotiator
import akka.http.scaladsl.server.Route
import akka.stream.scaladsl.StreamConverters
import scala.annotation.tailrec
import java.net.URL

object Frontend {
  val routes: Route =
    // format: off
    pathEndOrSingleSlash(getFromResource("public/index.html")) ~
    getFromResourceDirectoryWithGzip("public") ~
    path("robots.txt")(complete("User-agent: *\r\nDisallow: /\r\n"))
    // format: on

  // Copied from akka-http
  private def classLoader: ClassLoader = classOf[ActorSystem].getClassLoader

  private def withTrailingSlash(path: String): String =
    if (path.endsWith("/")) path else s"${path}/"

  private def safeJoinPaths(base: String,
                            path: Uri.Path,
                            log: LoggingAdapter,
                            separator: Char): String = {
    import java.lang.StringBuilder
    @tailrec def rec(p: Uri.Path, result: StringBuilder = new StringBuilder(base)): String =
      p match {
        case Uri.Path.Empty       => result.toString
        case Uri.Path.Slash(tail) => rec(tail, result.append(separator))
        case Uri.Path.Segment(head, tail) =>
          if (head.indexOf('/') >= 0 || head.indexOf('\\') >= 0 || head == "..") {
            log.warning(
              "File-system path for base [{}] and Uri.Path [{}] contains suspicious path segment [{}], " +
                "GET access was disallowed",
              base,
              path,
              head)
            ""
          } else rec(tail, result.append(head))
      }
    rec(if (path.startsWithSlash) path.tail else path)
  }

  private def conditionalFor(length: Long, lastModified: Long): Directive0 =
    extractSettings.flatMap(settings =>
      if (settings.fileGetConditional) {
        val tag                  = java.lang.Long.toHexString(lastModified ^ java.lang.Long.reverse(length))
        val lastModifiedDateTime = DateTime(math.min(lastModified, System.currentTimeMillis))
        conditional(EntityTag(tag), lastModifiedDateTime)
      } else pass)

  private val gzipEncoding        = List(HttpEncodings.gzip)
  private val gzipEncodingHeaders = List(`Content-Encoding`(HttpEncodings.gzip))

  private def getFromResourceDirectoryWithGzip(directoryName: String)(
      implicit resolver: ContentTypeResolver): Route =
    get {
      extractUnmatchedPath { path =>
        extractLog { log =>
          extractRequest { req =>
            val base = if (directoryName.isEmpty) "" else withTrailingSlash(directoryName)
            safeJoinPaths(base, path, log, separator = '/') match {
              case "" | "/" => reject
              case resourceName =>
                val negotiator = EncodingNegotiator(req.headers)
                val gzipArgs = negotiator.pickEncoding(gzipEncoding).flatMap { _ =>
                  val name = s"$resourceName.gz"
                  Option(classLoader.getResource(name)).map(url =>
                    (name, url, gzipEncodingHeaders))
                }
                gzipArgs.orElse(Option(classLoader.getResource(resourceName)).map(url =>
                  (resourceName, url, Nil))) match {
                  case Some((name, url, headers)) => completeFile(name, url, headers)
                  case _                          => reject
                }
            }
          }
        }
      }
    }

  private def completeFile(name: String, url: URL, headers: List[HttpHeader])(
      implicit resolver: ContentTypeResolver): Route =
    ResourceFile(url) match {
      case Some(ResourceFile(url, length, lastModified)) =>
        conditionalFor(length, lastModified) {
          if (length > 0) {
            respondWithHeaders(headers) {
              complete(
                HttpEntity.Default(resolver(name),
                                   length,
                                   StreamConverters.fromInputStream(() => url.openStream())))
            }
          } else complete(HttpEntity.Empty)
        }
      case _ => reject
    }
}
