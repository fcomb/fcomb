package io.fcomb.services.docker

import io.fcomb.services.docker.DockerApiMessages._
import io.fcomb.tests._
import akka.http.scaladsl._
import akka.http.scaladsl.server.Directives._
import org.scalatest._

class DockerApiClientSpec extends ActorSpec {
  "API client" must {
    "get version" in {
      val handler = pathPrefix("version") {
        get(complete(getFixture("docker/v1.19/version.json")))
      }
      startFakeHttpServer(handler) { port =>
        val dc = new DockerApiClient("localhost", port)
        dc.getVersion().map { res =>
          val version = Version(
            version = "1.7.1",
            apiVersion = "1.19",
            gitCommit = "df2f73d-dirty",
            goVersion = "go1.4.2",
            os = "linux",
            arch = "amd64",
            kernelVersion = Some("4.1.7-coreos"),
            experimental = None,
            buildTime = None
          )
          assert(res === version)
        }
      }
    }
  }
}
