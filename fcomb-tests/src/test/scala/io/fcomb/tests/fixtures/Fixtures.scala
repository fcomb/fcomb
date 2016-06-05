package io.fcomb.tests.fixtures

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}

object Fixtures {
  def await[T](fut: Future[T])(implicit timeout: Duration = 10.seconds): T =
    Await.result(fut, timeout)
}
