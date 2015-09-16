package io.fcomb.api.services.comb

import io.fcomb.api.services.Service
import akka.actor.ActorSystem
import akka.http.scaladsl.server.RequestContext
import akka.stream.Materializer
import io.fcomb.json._
import io.fcomb.models._
import io.fcomb.persist
import io.fcomb.services.user.ResetPassword
import scala.concurrent.{ ExecutionContext, Future }
import scala.language.implicitConversions

object CombService extends Service {
  // def create(
  //   implicit
  //   ec:           ExecutionContext,
  //   mat: Materializer
  // ) =
  //   authorization { user =>
  //     requestAsWithValidation { req: CombRequest =>
  //       persist.comb.Comb.create(
  //         userId = user.getId,
  //         name = req.name,
  //         slug = req.slug
  //       ).map(_.map(toResponse[comb.Comb, CombResponse]))
  //     }
  //   }

  // def update(
  //   id: Long
  // )(
  //   implicit
  //   ec:           ExecutionContext,
  //   mat: Materializer
  // ) =
  //   authorization { user =>
  //     requestAsWithValidation { req: CombRequest =>
  //       persist.comb.Comb.updateByRequest(id)(
  //         name = req.name,
  //         slug = req.slug
  //       ).map(_.map(toResponse[comb.Comb, CombResponse]))
  //     }
  //   }

  // def show(
  //   id: Long
  // )(
  //   implicit
  //   ec:           ExecutionContext,
  //   mat: Materializer
  // ) = ???

  // def destroy(
  //   id: Long
  // )(
  //   implicit
  //   ec:           ExecutionContext,
  //   mat: Materializer
  // ) = ???
}
