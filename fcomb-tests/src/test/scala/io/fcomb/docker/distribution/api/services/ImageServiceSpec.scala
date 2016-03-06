package io.fcomb.docker.distribution.server.api.services

import io.fcomb.docker.distribution.server.api.services.headers._
import io.fcomb.tests.SpecHelpers
import org.scalatest.{Matchers, WordSpec}
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.model.headers._
import akka.http.scaladsl.testkit.ScalatestRouteTest
import java.util.UUID

class ImageServiceSpec extends WordSpec with Matchers with ScalatestRouteTest with SpecHelpers {
  val route = Routes()
  val imageName = "library/test-image_2016"
  val blob = getFixture("docker/distribution/blob")

  "The image service" should {
    "return a uuid for POST requests to the start upload path" in {
      Post(s"/v2/$imageName/blobs/uploads/") ~> route ~> check {
        status === StatusCodes.Accepted
        responseAs[String] shouldEqual ""
        val uuid = header[`Docker-Upload-Uuid`].map(h => UUID.fromString(h.value)).get
        header[Location].get shouldEqual Location(s"/v2/$imageName/blobs/uploads/$uuid")
        header[RangeCustom].get shouldEqual RangeCustom(0L, 0L)
      }
    }
  }
}
