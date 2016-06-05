package io.fcomb.docker.distribution.server.api

import io.fcomb.docker.distribution.server.headers.`Docker-Distribution-Api-Version`
import io.fcomb.tests.fixtures._
import io.fcomb.tests._
import org.scalatest.{Matchers, WordSpec}
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.model.headers.BasicHttpCredentials
import akka.http.scaladsl.testkit.ScalatestRouteTest
import io.fcomb.docker.distribution.server.Routes
import io.circe.Json
import de.heikoseeberger.akkahttpcirce.CirceSupport._

class AuthenticationHandlerSpec extends WordSpec with Matchers with ScalatestRouteTest with PersistSpec {
  val route = Routes()
  val credentials = BasicHttpCredentials(UsersRepoFixture.username, UsersRepoFixture.password)

  "The authentication handler" should {
    "return a version for GET requests to the version path" in {
      Fixtures.await(UsersRepoFixture.create())

      Get("/v2/") ~> addCredentials(credentials) ~> route ~> check {
        status shouldEqual StatusCodes.OK
        entityAs[Json] shouldEqual Json.obj()
        header[`Docker-Distribution-Api-Version`].get.value shouldEqual "registry/2.0"
      }
    }
  }
}
