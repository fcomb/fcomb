package io.fcomb.tests.fixtures

import cats.data.Validated
import io.fcomb.models.User
import io.fcomb.persist.UsersRepo
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

object UsersRepoFixture {
  val email = "test@fcomb.io"
  val username = "test"
  val password = "password"
  val fullName = Some("Test Test")

  def create(
    email:    String         = email,
    username: String         = username,
    password: String         = password,
    fullName: Option[String] = fullName
  ): Future[User] = {
    for {
      Validated.Valid(user) ← UsersRepo.create(
        email = email,
        username = username,
        password = password,
        fullName = fullName
      )
    } yield user
  }
}
