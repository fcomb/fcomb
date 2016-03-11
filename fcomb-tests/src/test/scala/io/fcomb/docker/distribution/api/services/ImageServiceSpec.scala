package io.fcomb.docker.distribution.server.api.services

import io.fcomb.docker.distribution.server.api.services.headers._
import io.fcomb.tests.{SpecHelpers, PersistSpec}
import io.fcomb.tests.fixtures.Fixtures
import org.scalatest.{Matchers, WordSpec}
import org.scalatest.concurrent.ScalaFutures
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.model.headers._
import akka.http.scaladsl.testkit.ScalatestRouteTest
import java.util.UUID

class ImageServiceSpec extends WordSpec with Matchers with ScalatestRouteTest with SpecHelpers with ScalaFutures with PersistSpec {
  val route = Routes()
  val imageName = "library/test-image_2016"
  val blob = getFixture("docker/distribution/blob")

  "The image service" should {
    "return a uuid for POST requests to the start upload path" in {
      Fixtures.await(Fixtures.User.create())

      Post(s"/v2/$imageName/blobs/uploads/") ~> route ~> check {
        status === StatusCodes.Accepted
        responseAs[String] shouldEqual ""
        val uuid = header[`Docker-Upload-Uuid`].map(h ⇒ UUID.fromString(h.value)).get
        header[Location].get shouldEqual Location(s"/v2/$imageName/blobs/uploads/$uuid")
        header[RangeCustom].get shouldEqual RangeCustom(0L, 0L)
      }
    }

    "test registry" in {
      import org.scalatest.concurrent._
      import akka.http._
      import akka.http.scaladsl._, model._
      import akka.stream._
      import akka.stream.scaladsl._
      import akka.util.ByteString
      import scala.concurrent.duration._

      def registryCall(
        method:  HttpMethod,
        uri:     String,
        entity:  RequestEntity    = HttpEntity.Empty,
        headers: List[HttpHeader] = List.empty
      ) = {
        Source
          .single(HttpRequest(
            uri = uri,
            method = method,
            entity = entity,
            headers = headers
          ))
          .via(Http().outgoingConnection("coreos", 5000))
          .runWith(Sink.head)
          .flatMap { res ⇒
            res.entity.toStrict(1.second).map(_.getData).map { data ⇒
              (data, res.headers)
            }
          }
      }

      val (res, headers) = registryCall(HttpMethods.GET, "/v2/").futureValue
      res shouldBe ByteString("{}")
    }
  }
}
