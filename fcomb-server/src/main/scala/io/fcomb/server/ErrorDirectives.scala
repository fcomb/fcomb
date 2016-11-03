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

package io.fcomb.server

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import cats.data.Validated
import io.circe.Encoder
import io.fcomb.json.models.errors.Formats._
import io.fcomb.models.errors.{Error, Errors}
import io.fcomb.models.IdLens
import io.fcomb.akka.http.CirceSupport._
import io.fcomb.server.CommonDirectives._
import io.fcomb.validation.ValidationResult
import scala.concurrent.Future

object ErrorDirectives {
  def completeErrors(xs: List[Error]): Route =
    complete((StatusCodes.BadRequest, Errors(xs)))

  def completeErrorsOrNoContent[T](res: ValidationResult[T]): Route =
    res match {
      case Validated.Valid(_)    => completeNoContent()
      case Validated.Invalid(xs) => completeErrors(xs)
    }

  def completeErrorsOrNoContent[T](fut: => Future[ValidationResult[T]]): Route =
    onSuccess(fut)(completeErrorsOrNoContent(_))

  def completeAsAccepted[T: Encoder](res: ValidationResult[T]): Route =
    res match {
      case Validated.Valid(rs)   => complete((StatusCodes.Accepted, rs))
      case Validated.Invalid(xs) => completeErrors(xs)
    }

  def completeAsAccepted[T: Encoder](fut: Future[ValidationResult[T]]): Route =
    onSuccess(fut)(completeAsAccepted(_))

  def completeAsCreated[T: Encoder](res: ValidationResult[T], prefix: String)(
      implicit idLens: IdLens[T]): Route =
    res match {
      case Validated.Valid(rs)   => completeCreated(rs, prefix)
      case Validated.Invalid(xs) => completeErrors(xs)
    }

  def completeAsCreated[T: Encoder](fut: Future[ValidationResult[T]], prefix: String)(
      implicit idLens: IdLens[T]): Route =
    onSuccess(fut)(completeAsCreated(_, prefix))
}
