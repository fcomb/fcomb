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

  lazy val config          = ConfigFactory.load().getConfig("fcomb-server")
  lazy val actorSystemName = config.getString("actor-system-name")
  lazy val jdbcConfig      = config.getConfig("jdbc")
  lazy val jdbcConfigSlick = config.getConfig("jdbc-slick")

  object docker {
    object distribution {
      lazy val imageStorage = config.getString("docker.distribution.image-storage")
      lazy val realm        = config.getString("docker.distribution.realm")

      object gc {
        lazy val outdatedPeriod: JDuration =
          config.get[JDuration]("docker.distribution.gc.outdated-period").value
        lazy val outdatedCheckInterval: FiniteDuration =
          config.get[JDuration]("docker.distribution.gc.outdated-check-interval").value
        lazy val deletingCheckInterval: FiniteDuration =
          config.get[JDuration]("docker.distribution.gc.deleting-check-interval").value
      }
    }
  }

  object security {
    lazy val isOpenSignUp = config.getBoolean("security.open-sign-up")
    lazy val isAnonymousPublicRepositories =
      config.getBoolean("security.anonymous-public-repositories")
  }

  object jwt {
    lazy val secret           = config.getString("jwt.secret")
    lazy val sessionTtl       = config.get[JDuration]("jwt.session-ttl").value.getSeconds
    lazy val resetPasswordTtl = config.get[JDuration]("jwt.reset-password-ttl").value.getSeconds
  }
}
