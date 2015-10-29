package io.fcomb.services.docker

import io.fcomb.services.docker.DockerApiMessages._
import io.fcomb.tests._
import akka.http.scaladsl._
import akka.http.scaladsl.server.Directives._
import org.scalatest._
import java.time.ZonedDateTime

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

    "get information" in {
      val handler = pathPrefix("info") {
        get(complete(getFixture("docker/v1.19/info.json")))
      }
      startFakeHttpServer(handler) { port =>
        val dc = new DockerApiClient("localhost", port)
        dc.getInformation().map { res =>
          val serviceConfig = ServiceConfig(
            insecureRegistryCidrs = List("127.0.0.0/8"),
            indexConfigs = Map("docker.io" -> IndexInfo(
              name = "docker.io",
              mirrors = List.empty,
              isSecure = true,
              isOfficial = true
            ))
          )
          val information = Information(
            id = "C2QK:MS2Z:LS22:NQTI:IGXQ:F3PR:C2LX:3GVG:YA5I:ZML6:MIJF:S66D",
            continers = 30,
            images = 83,
            driver = "overlay",
            driverStatus = List(List("Backing Filesystem", "extfs")),
            isMemoryLimit = true,
            isSwapLimit = true,
            isCpuCfsPeriod = true,
            isCpuCfsQuota = true,
            isIpv4Forwarding = true,
            isBridgeNfIptables = false,
            isBridgeNfIp6tables = false,
            isDebug = false,
            fileDescriptors = 22,
            isOomKillDisable = true,
            goroutines = 41,
            systemTime = ZonedDateTime.parse("2015-10-29T20:28:54.681345419Z"),
            executionDriver = "native-0.2",
            loggingDriver = Some("json-file"),
            eventsListeners = 0,
            kernelVersion = "4.1.7-coreos",
            operatingSystem = "CoreOS 766.4.0",
            indexServerAddress = "https://index.docker.io/v1/",
            registryConfig = Some(serviceConfig),
            initSha1 = "8c20958b95c1e3e14b897f7400bc349ba1363511",
            initPath = "/usr/libexec/docker/dockerinit",
            cpus = 2,
            memory = 2102759424,
            dockerRootDir = "/var/lib/docker",
            httpProxy = None,
            httpsProxy = None,
            noProxy = None,
            name = Some("coreos"),
            labels = List.empty,
            isExperimentalBuild = false
          )
          assert(res === information)
        }
      }
    }
  }
}
