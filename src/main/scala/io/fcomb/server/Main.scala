package io.fcomb.server

import io.fcomb.Db
import io.fcomb.utils.Config
import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
// import kamon.Kamon
import scala.util.{ Success, Failure }
import scala.language.existentials

object Main extends App {
  // Kamon.start()

  implicit val system = ActorSystem("fcomb-server", Config.config)
  implicit val materializer = ActorMaterializer()
  import system.dispatcher


  trait Effect
  object Effect {
    trait Plain extends Effect
    trait Future extends Effect
    trait IO extends Effect
    trait DBIOAction extends Effect
    trait All extends Plain with Future with IO with DBIOAction
  }

  sealed trait ValidationN[+E <: Effect] {
    def `::`[E2 <: Effect](b2: ValidationN[E2]): ValidationN[E with E2] =
      ValidationContainer[E with E2](List(this, b2))
  }

  // TODO AndContainer, OrContainer, NotContainer

  private case class ValidationContainer[E2 <: Effect](l: List[ValidationN[Effect]]) extends ValidationN[E2]

  case class FutureValidation() extends ValidationN[Effect.Future]

  case class PlainValidation() extends ValidationN[Effect.Plain]

  def validateP[T <: Effect.Plain](v: ValidationN[T])(implicit eq: ValidationN[T] =:= ValidationN[Effect.Plain]): Int = 0

  def validateF[T <: ValidationN[Effect.Future]](v: T): Int = 1

  val l = FutureValidation() :: PlainValidation()

  validateP(PlainValidation())
  validateP(PlainValidation() :: PlainValidation())
  // validateP(l)
  validateF(l)
  // validateF(PlainValidation())

  //
  // (for {
  //   _ <- Db.migrate()
  //   _ <- HttpApiService.start(Config.config)
  // } yield ()).onComplete {
  //   case Success(_) =>
  //     println(s"res: ${io.fcomb.persist.User.res}")
  //   case Failure(e) =>
  //     // Kamon.shutdown()
  //     system.terminate()
  //     throw e
  // }
}
