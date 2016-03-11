package io.fcomb.tests.fixtures

import io.fcomb.{persist ⇒ P}
import scala.concurrent.{Await, Future}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scalaz._
import org.slf4j.LoggerFactory

object Fixtures {
  lazy val logger = LoggerFactory.getLogger(getClass)

  def await[T](fut: Future[T])(implicit timeout: Duration = 10.seconds): T =
    Await.result(fut, timeout)

  object User {
    val email = "test@fcomb.io"
    val username = "test"
    val password = "password"
    val fullName = Some("Test Test")

    def create(
      email:    String         = email,
      username: String         = username,
      password: String         = password,
      fullName: Option[String] = fullName
    ) =
      P.User.create(
        email = email,
        username = username,
        password = password,
        fullName = fullName
      ).map(getSuccess)
  }

  private def getSuccess[T](res: Validation[_, T]) =
    res match {
      case Success(res) ⇒ res
      case Failure(e) ⇒
        logger.error(e.toString)
        throw new IllegalStateException(e.toString)
    }
}
