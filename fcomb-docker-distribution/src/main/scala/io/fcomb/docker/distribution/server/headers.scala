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

package io.fcomb.docker.distribution.server

import akka.http.scaladsl.model.headers._
import java.util.UUID

object headers {
  final case class `Docker-Distribution-Api-Version`(version: String) extends CustomHeader {
    def renderInRequests  = false
    def renderInResponses = true
    def name: String      = "Docker-Distribution-Api-Version"
    def value: String     = s"registry/$version"
  }

  final case class `Docker-Upload-Uuid`(uuid: UUID) extends CustomHeader {
    def renderInRequests  = false
    def renderInResponses = true
    def name: String      = "Docker-Upload-Uuid"
    def value: String     = uuid.toString
  }

  final case class `Docker-Content-Digest`(schema: String, digest: String) extends CustomHeader {
    def renderInRequests  = false
    def renderInResponses = true
    def name: String      = "Docker-Content-Digest"
    def value: String     = s"$schema:$digest"
  }

  final case class RangeCustom(first: Long, last: Long) extends CustomHeader {
    def renderInRequests  = false
    def renderInResponses = true
    def name: String      = "Range"
    def value: String     = s"$first-$last"
  }
}
