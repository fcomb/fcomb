package io.fcomb

import scalaz._, Scalaz._

package object utils {
  def tryE[T](value: => T): String \/ T =
    try \/-(value)
    catch {
      case e: Throwable => -\/(e.getMessage)
    }
}
