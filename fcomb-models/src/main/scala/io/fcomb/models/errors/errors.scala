package io.fcomb.models.errors

import scala.util.control.NoStackTrace

trait Error

trait ErrorResponse

@SerialVersionUID(1L)
case class ThrowableError(error: Error) extends Throwable with NoStackTrace
