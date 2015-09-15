package io.fcomb.api.services

import io.fcomb.models._
import io.fcomb.persist
import io.fcomb.json._
import akka.http.scaladsl.model.HttpResponse
import akka.http.scaladsl.server.RequestContext
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.Materializer
import scala.language.implicitConversions
import scala.concurrent.{ ExecutionContext, Future }
import shapeless._, contrib.scalaz._, syntax.std.function._
import scalaz._
import scalaz.syntax.foldable._
import ResponseConversions._

object SessionService extends Service {
  // def create(
  //   implicit
  //   ec:           ExecutionContext,
  //   mat: Materializer
  // ) =
  //   requestAsWithValidation { req: SessionRequest =>
  //     persist.Session
  //       .create(req)
  //       .map(_.map(toResponse[Session, SessionResponse]))
  //   }

  // def destroy(
  //   implicit
  //   ec:           ExecutionContext,
  //   mat: Materializer
  // ) =
  //   ??? // TODO
}
