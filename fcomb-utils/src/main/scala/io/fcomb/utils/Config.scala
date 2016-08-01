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

package io.fcomb.utils

import com.typesafe.config.ConfigFactory
import configs.syntax._
import java.time.{Duration => JDuration}
import scala.concurrent.duration.{Duration, FiniteDuration}
import scala.language.implicitConversions

// TODO: migrate to case class instead of object
object Config {
  private implicit def toFiniteDuration(d: JDuration): FiniteDuration =
    Duration.fromNanos(d.toNanos)

  val config          = ConfigFactory.load().getConfig("fcomb-server")
  val actorSystemName = config.getString("actor-system-name")
  val jdbcConfig      = config.getConfig("jdbc")
  val jdbcConfigSlick = config.getConfig("jdbc-slick")

  object docker {
    object distribution {
      val imageStorage = config.getString("docker.distribution.image-storage")
      val realm        = config.getString("docker.distribution.realm")

      object gc {
        val outdatedPeriod: JDuration =
          config.get[JDuration]("docker.distribution.gc.outdated-period").value
        val outdatedCheckInterval: FiniteDuration =
          config.get[JDuration]("docker.distribution.gc.outdated-check-interval").value
        val deletingCheckInterval: FiniteDuration =
          config.get[JDuration]("docker.distribution.gc.deleting-check-interval").value
      }
    }
  }

  object security {
    val isOpenSignUp                  = config.getBoolean("security.open-sign-up")
    val isAnonymousPublicRepositories = config.getBoolean("security.anonymous-public-repositories")
  }

  object session {
    val secret              = config.getString("session.secret")
    val ttl                 = config.getLong("session.ttl")
    val passwordResetSecret = config.getString("session.passwordResetSecret")
    val passwordResetTtl    = config.getLong("session.passwordResetTtl")
  }

  val smtp = config.getConfig("smtp")
}
