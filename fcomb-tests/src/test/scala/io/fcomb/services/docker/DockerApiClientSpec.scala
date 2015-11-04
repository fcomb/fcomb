package io.fcomb.services.docker

import io.fcomb.services.docker.DockerApiMessages._
import io.fcomb.tests._
import io.fcomb.utils.Units._
import akka.http.scaladsl.server.Directives._
import java.time.ZonedDateTime

class DockerApiClientSpec extends ActorSpec {
  "API client" must {
    // "get version" in {
    //   val handler = pathPrefix("version") {
    //     get(complete(getFixture("docker/v1.19/version.json")))
    //   }
    //   startFakeHttpServer(handler) { port =>
    //     val dc = new DockerApiClient("localhost", port)
    //     dc.getVersion().map { res =>
    //       val version = Version(
    //         version = "1.7.1",
    //         apiVersion = "1.19",
    //         gitCommit = "df2f73d-dirty",
    //         goVersion = "go1.4.2",
    //         os = "linux",
    //         arch = "amd64",
    //         kernelVersion = Some("4.1.7-coreos"),
    //         experimental = None,
    //         buildTime = None
    //       )
    //       assert(res === version)
    //     }
    //   }
    // }

    // "get information" in {
    //   val handler = pathPrefix("info") {
    //     get(complete(getFixture("docker/v1.19/info.json")))
    //   }
    //   startFakeHttpServer(handler) { port =>
    //     val dc = new DockerApiClient("localhost", port)
    //     dc.getInformation().map { res =>
    //       val serviceConfig = ServiceConfig(
    //         insecureRegistryCidrs = List("127.0.0.0/8"),
    //         indexConfigs = Map("docker.io" -> IndexInfo(
    //           name = "docker.io",
    //           mirrors = List.empty,
    //           isSecure = true,
    //           isOfficial = true
    //         ))
    //       )
    //       val information = Information(
    //         id = "C2QK:MS2Z:LS22:NQTI:IGXQ:F3PR:C2LX:3GVG:YA5I:ZML6:MIJF:S66D",
    //         continers = 30,
    //         images = 83,
    //         driver = "overlay",
    //         driverStatus = List(List("Backing Filesystem", "extfs")),
    //         isMemoryLimit = true,
    //         isSwapLimit = true,
    //         isCpuCfsPeriod = true,
    //         isCpuCfsQuota = true,
    //         isIpv4Forwarding = true,
    //         isBridgeNfIptables = false,
    //         isBridgeNfIp6tables = false,
    //         isDebug = false,
    //         fileDescriptors = 22,
    //         isOomKillDisable = true,
    //         goroutines = 41,
    //         systemTime = ZonedDateTime.parse("2015-10-29T20:28:54.681345419Z"),
    //         executionDriver = "native-0.2",
    //         loggingDriver = Some("json-file"),
    //         eventsListeners = 0,
    //         kernelVersion = "4.1.7-coreos",
    //         operatingSystem = "CoreOS 766.4.0",
    //         indexServerAddress = "https://index.docker.io/v1/",
    //         registryConfig = Some(serviceConfig),
    //         initSha1 = "8c20958b95c1e3e14b897f7400bc349ba1363511",
    //         initPath = "/usr/libexec/docker/dockerinit",
    //         cpus = 2,
    //         memory = 2102759424,
    //         dockerRootDir = "/var/lib/docker",
    //         httpProxy = None,
    //         httpsProxy = None,
    //         noProxy = None,
    //         name = Some("coreos"),
    //         labels = List.empty,
    //         isExperimentalBuild = false
    //       )
    //       assert(res === information)
    //     }
    //   }
    // }

    // "get containers" in {
    //   val handler = pathPrefix("containers" / "json") {
    //     get(complete(getFixture("docker/v1.19/containers.json")))
    //   }
    //   startFakeHttpServer(handler) { port =>
    //     val dc = new DockerApiClient("localhost", port)
    //     dc.getContainers().map { res =>
    //       val containers = List(
    //         ContainerItem(
    //           id = "c7c8678a5a0e0b503afed4c5f7c88332097b2d271a41722c4cc56fb98fbb5616",
    //           names = List("/ubuntu1404"),
    //           image = "ubuntu:14.04",
    //           command = "/bin/bash",
    //           createdAt = ZonedDateTime.parse("2015-10-28T18:01:27+03:00"),
    //           status = "Up 29 hours",
    //           ports = List(Port(
    //             privatePort = 2375,
    //             publicPort = None,
    //             kind = PortKind.Tcp,
    //             ip = None
    //           )),
    //           sizeRw = Some(109626507633L),
    //           sizeRootFs = Some(109789320076L)
    //         ),
    //         ContainerItem(
    //           id = "d2014860647461e6924c1fd39b9806ed322378938d4342ebd5498d9d21d9abaa",
    //           names = List("/docker"),
    //           image = "docker:rc",
    //           command = "docker-entrypoint.sh /bin/sh",
    //           createdAt = ZonedDateTime.parse("2015-10-27T10:26:17+03:00"),
    //           status = "Up 30 hours",
    //           ports = List(Port(
    //             privatePort = 2375,
    //             publicPort = None,
    //             kind = PortKind.Tcp,
    //             ip = None
    //           )),
    //           sizeRw = Some(109533280374L),
    //           sizeRootFs = Some(109569328002L)
    //         )
    //       )
    //       assert(res === containers)
    //     }
    //   }
    // }

    "create container" in {
      // val handler = pathPrefix("containers" / "create") {
      //   post(complete(getFixture("docker/v1.19/create_container.json")))
      // }
      // startFakeHttpServer(handler) { port =>
      val dc = new DockerApiClient("coreos", 2375)
      import scala.concurrent._
      val hostConfig = HostConfig(
        binds = List(VolumeBindPath.VolumeHostPath("/tmp", "/tmp", MountMode.rw)),
        links = List(ContainerLink("nginx", "nginx")),
        memory = Some(5.MB),
        memorySwap = Some(5.MB),
        cpuShares = Some(100),
        cpuPeriod = Some(100000),
        cpusetCpus = Some("0,1"),
        cpusetMems = Some("0,1"),
        blockIoWeight = Some(300),
        memorySwappiness = Some(50),
        isOomKillDisable = true,
        portBindings = Map(ExposePort.Tcp(80) -> List(PortBinding(80, Some("0.0.0.0")))),
        isPublishAllPorts = true,
        isPrivileged = true,
        isReadonlyRootfs = true,
        dns = List("8.8.8.8"),
        dnsSearch = List("8.8.8.8"),
        extraHosts = List(ExtraHost("test.io", "1.2.3.4")),
        volumesFrom = List(VolumeFrom("nginx", MountMode.ro)),
        ipcMode = Some(IpcMode.Host),
        pidMode = Some(PidMode.Host),
        utsMode = Some(UtsMode.Host),
        capacityAdd = List(Capacity.Mknod),
        capacityDrop = List(Capacity.SetPcap),
        restartPolicy = RestartPolicy.Always,
        networkMode = NetworkMode.Bridge,
        devices = List(DeviceMapping("/dev/tty9", "/dev/tty9", "mrw")),
        ulimits = List(Ulimit("nofile", 1024, 2048)),
        logConfig = Some(LogConfig(LogDriver.Syslog)),
        securityOpt = List("label:type:test"),
        cgroupParent = Some("/docker_test_cgroup")
      )
      val config = ContainerCreate(
        image = "ubuntu",
        hostname = Some("hostname"),
        domainName = Some("domainname"),
        user = Some("user"),
        isAttachStdin = true,
        isAttachStdout = true,
        isAttachStderr = true,
        isTty = true,
        isOpenStdin = true,
        isStdinOnce = true,
        env = List("TEST=test:ttest"),
        command = List("ls"),
        entrypoint = Some("ls"),
        labels = Map("label1" -> "value"),
        mounts = List(MountPoint("/etc", "/etc", Set(MountMode.ro), false)),
        isNetworkDisabled = true,
        workingDirectory = Some("/tmp"),
        macAddress = Some("bb:cc:ff:22:11:11"),
        exposedPorts = Set(ExposePort.Tcp(80)),
        hostConfig = hostConfig
      )
      Await.result(dc.createContainer(config).map {
        case res: ContainerCreateResponse =>
          assert(res.id.nonEmpty)
          assert(res.warnings.isEmpty)
      }, timeout)
      // }
    }


  }
}
