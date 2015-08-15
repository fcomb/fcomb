package io.fcomb.api.services.comb

import io.fcomb.api.services.ApiService
import akka.actor.ActorSystem
import akka.http.scaladsl.server.RequestContext
import akka.stream.Materializer
import io.fcomb.json._
import io.fcomb.models._
import io.fcomb.models.ResponseConversions._
import io.fcomb.persist
import io.fcomb.services.user.ResetPassword
import java.util.UUID
import scala.concurrent.{ ExecutionContext, Future }
import scala.language.implicitConversions

object CombMethodService extends ApiService {
  def create(combId: UUID)(
    implicit
    ec:           ExecutionContext,
    materializer: Materializer
  ) =
    authorization { user =>
      requestAsWithValidation { req: CombMethodRequest =>
        persist.comb.CombMethod.create(
          combId = combId,
          kind = req.kind,
          uri = req.uri,
          endpoint = req.endpoint
        ).map(_.map(toResponse[comb.CombMethod, CombMethodResponse]))
      }
    }

  // def update(
  //   id: UUID
  // )(
  //   implicit
  //   ec:           ExecutionContext,
  //   materializer: Materializer
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
  //   id: UUID
  // )(
  //   implicit
  //   ec:           ExecutionContext,
  //   materializer: Materializer
  // ) = ???

  // def destroy(
  //   id: UUID
  // )(
  //   implicit
  //   ec:           ExecutionContext,
  //   materializer: Materializer
  // ) = ???
}
