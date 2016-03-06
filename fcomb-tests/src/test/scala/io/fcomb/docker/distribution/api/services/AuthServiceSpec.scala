package io.fcomb.docker.distribution.server.api.services

import org.scalatest.{Matchers, WordSpec}
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import akka.http.scaladsl.testkit.ScalatestRouteTest
import spray.json._

class AuthServiceSpec extends WordSpec with Matchers with ScalatestRouteTest {
  val route = Routes()

  "The auth service" should {
    "return a version for GET requests to the version path" in {
      Get("/v2") ~> route ~> check {
        status === StatusCodes.OK
        entityAs[JsValue] shouldEqual JsObject.empty
      }
    }
  }
}
