package io.fcomb.docker.api

object StdStream extends Enumeration {
  type StdStream = Value

  val In = Value(0)
  val Out = Value(1)
  val Err = Value(2)
}
