package io.fcomb.docker.distribution.server.api.services

import io.fcomb.docker.distribution.server.api.services.headers._
import io.fcomb.utils.StringUtils
import io.fcomb.tests.{SpecHelpers, PersistSpec}
import akka.http._
import akka.http.scaladsl._, model._
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.model.headers._
import akka.http.scaladsl.testkit.ScalatestRouteTest
import akka.stream._
import akka.stream.scaladsl._
import akka.util.ByteString
import org.scalatest.concurrent._
import scala.concurrent.duration._
import org.scalatest.{Matchers, WordSpec}
import org.scalatest.concurrent.ScalaFutures
import java.security.MessageDigest

class DistributionApiSpec extends WordSpec with Matchers with ScalatestRouteTest
    with SpecHelpers with ScalaFutures {
  val imageName = "library/test-image_2016"
  val bs = ByteString(getFixture("docker/distribution/blob"))
  val digest = {
    val md = MessageDigest.getInstance("SHA-256")
    md.update(bs.toArray)
    StringUtils.hexify(md.digest)
  }

  "distribution api" should {
    "test registry" in {
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

      val (res, headers) = registryCall(
        HttpMethods.POST,
        s"/v2/lolca/images/blobs/uploads/?digest=sha256:$digest",
        HttpEntity(ContentTypes.`application/octet-stream`, bs)
      ).futureValue
      res shouldBe ByteString.empty
      println(headers)
    }
  }
}
