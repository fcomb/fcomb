package io.fcomb

import scalaz._

package object utils {
  def tryE[T](value: => T): Throwable \/ T =
    try \/-(value)
    catch {
      case e: Throwable => -\/(e)
    }
}
