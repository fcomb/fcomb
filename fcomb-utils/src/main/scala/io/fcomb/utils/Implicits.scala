package io.fcomb.utils

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import com.typesafe.config.ConfigFactory

object Implicits {
  object global {
    private var sys: ActorSystem = _
    private var mat: ActorMaterializer = _

    def system: ActorSystem = sys
    def materializer: ActorMaterializer = mat

    def apply(system: ActorSystem, materializer: ActorMaterializer) = {
      sys = system
      mat = materializer
    }
  }
}
