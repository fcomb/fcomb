package io.fcomb.models.errors

import scala.util.control.NoStackTrace

protected[errors] trait Error

protected[errors] trait ErrorResponse

@SerialVersionUID(1L)
case class ThrowableError(error: Error) extends Throwable with NoStackTrace
