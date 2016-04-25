package io.fcomb.docker.distribution.server.api

import io.fcomb.docker.distribution.server.api.headers.`Docker-Distribution-Api-Version`
import io.fcomb.tests.fixtures.Fixtures
import io.fcomb.tests._
import org.scalatest.{Matchers, WordSpec}
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.model.headers.BasicHttpCredentials
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import akka.http.scaladsl.testkit.ScalatestRouteTest
import spray.json._

class AuthServiceSpec extends WordSpec with Matchers with ScalatestRouteTest with PersistSpec {
  val route = Routes()
  val credentials = BasicHttpCredentials(Fixtures.User.username, Fixtures.User.password)

  "The auth service" should {
    "return a version for GET requests to the version path" in {
      Fixtures.await(Fixtures.User.create())

      Get("/v2/") ~> addCredentials(credentials) ~> route ~> check {
        status === StatusCodes.OK
        entityAs[JsValue] shouldEqual JsObject.empty
        header[`Docker-Distribution-Api-Version`].get.value shouldEqual "registry/2.0"
      }
    }
  }
}
