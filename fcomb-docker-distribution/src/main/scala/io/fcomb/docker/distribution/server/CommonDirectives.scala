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

package io.fcomb.docker.distribution.server

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import io.fcomb.json.models.errors.docker.distribution.Formats._
import io.fcomb.models.errors.docker.distribution.{DistributionError, DistributionErrors}
import io.fcomb.models.errors.docker.distribution.DistributionErrorCode._
import io.fcomb.server.CirceSupport._

object CommonDirectives {
  def completeError(error: DistributionError): Route = {
    val status = error.code match {
      case DigestInvalid | ManifestBlobUnknown | ManifestUnverified | ManifestInvalid |
          NameInvalid | TagInvalid | SizeInvalid =>
        StatusCodes.BadRequest
      case BlobUnknown | BlobUploadInvalid | BlobUploadUnknown | ManifestInvalid |
          ManifestUnknown | NameUnknown =>
        StatusCodes.NotFound
      case Denied       => StatusCodes.Forbidden
      case Unauthorized => StatusCodes.Unauthorized
      case Unknown      => StatusCodes.InternalServerError
      case Unsupported  => StatusCodes.MethodNotAllowed
    }
    complete((status, DistributionErrors.from(error)))
  }
}
