package akka.http

import akka.http.scaladsl.settings.ClientConnectionSettings
import akka.http.impl.settings.ClientConnectionSettingsImpl
import scala.concurrent.duration.Duration

package object settings {
  def clientSettingsWithIdleTimeout(
    settings: ClientConnectionSettings,
    duration: Option[Duration]
  ) =
    duration.foldLeft(settings) { (s, d) ⇒
      s.asInstanceOf[ClientConnectionSettingsImpl].copy(idleTimeout = d)
    }
}
