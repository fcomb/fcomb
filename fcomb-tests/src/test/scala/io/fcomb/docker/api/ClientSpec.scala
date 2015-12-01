package io.fcomb.docker.api

import io.fcomb.docker.api.methods._
import io.fcomb.docker.api.methods.ContainerMethods._
import io.fcomb.docker.api.methods.ImageMethods._
import io.fcomb.docker.api.methods.MiscMethods._
import io.fcomb.tests._
import io.fcomb.utils.Units._
import akka.http.scaladsl.server.Directives._
import akka.util.ByteString
import akka.stream.scaladsl.Sink
import spray.json._
import java.time.ZonedDateTime

class ClientSpec extends ActorSpec {
  "API client" must {
    val containerId = "52e3163a6ac79040dd3af67525e3ee8bcc7b4de6650781304dc4982e95ca20ee"

    "get version" in {
      val handler = pathPrefix("version") {
        get(complete(getFixture("docker/v1.19/version.json")))
      }
      startFakeHttpServer(handler) { port =>
        val dc = new Client("localhost", port)
        dc.version().map { res =>
          val version = Version(
            version = "1.7.1",
            apiVersion = "1.19",
            gitCommit = "df2f73d-dirty",
            goVersion = "go1.4.2",
            os = "linux",
            arch = "amd64",
            kernelVersion = Some("4.1.7-coreos"),
            experimental = false,
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
        val dc = new Client("localhost", port)
        dc.information().map { res =>
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
            labels = Map.empty,
            isExperimentalBuild = false
          )
          assert(res === information)
        }
      }
    }

    "get containers" in {
      val handler = pathPrefix("containers" / "json") {
        get(complete(getFixture("docker/v1.19/containers.json")))
      }
      startFakeHttpServer(handler) { port =>
        val dc = new Client("localhost", port)
        dc.containers().map { res =>
          val containers = List(
            ContainerItem(
              id = "c7c8678a5a0e0b503afed4c5f7c88332097b2d271a41722c4cc56fb98fbb5616",
              names = List("/ubuntu1404"),
              image = "ubuntu:14.04",
              command = "/bin/bash",
              createdAt = ZonedDateTime.parse("2015-10-28T18:01:27+03:00"),
              status = "Up 29 hours",
              ports = List(Port(
                privatePort = 2375,
                publicPort = None,
                kind = PortKind.Tcp,
                ip = None
              )),
              sizeRw = Some(109626507633L),
              sizeRootFs = Some(109789320076L)
            ),
            ContainerItem(
              id = "d2014860647461e6924c1fd39b9806ed322378938d4342ebd5498d9d21d9abaa",
              names = List("/docker"),
              image = "docker:rc",
              command = "docker-entrypoint.sh /bin/sh",
              createdAt = ZonedDateTime.parse("2015-10-27T10:26:17+03:00"),
              status = "Up 30 hours",
              ports = List(Port(
                privatePort = 2375,
                publicPort = None,
                kind = PortKind.Tcp,
                ip = None
              )),
              sizeRw = Some(109533280374L),
              sizeRootFs = Some(109569328002L)
            )
          )
          assert(res === containers)
        }
      }
    }

    "create container" in {
      val handler = pathPrefix("containers" / "create") {
        post { ctx =>
          val name = ctx.request.uri.query().get("name")
          assert(name.contains("test_container"))
          ctx.request.entity.dataBytes.runFold(ByteString.empty)(_ ++ _)
            .flatMap { bs =>
              val req = getFixture("docker/v1.19/create_container_request.json").parseJson
              assert(bs.utf8String.parseJson == req)
              ctx.complete(getFixture("docker/v1.19/create_container.json"))
            }
        }
      }
      startFakeHttpServer(handler) { port =>
        val dc = new Client("localhost", port)
        val hostConfig = HostConfig(
          binds = List(VolumeBindPath.VolumeHostPath("/tmp", "/tmp", MountMode.rw)),
          links = List(ContainerLink("nginx", "nginx")),
          memory = Some(5.MB),
          memorySwap = Some(5.MB),
          cpuShares = Some(100),
          cpuPeriod = Some(100000),
          cpusetCpus = Some("0,1"),
          cpusetMems = Some("0,1"),
          cpuQuota = Some(10000),
          blockIoWeight = Some(300),
          memorySwappiness = Some(50),
          isOomKillDisable = true,
          portBindings = Map(
            ExposePort.Tcp(80) -> List(PortBinding(80, Some("0.0.0.0")))
          ),
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
        dc.containerCreate(config, name = Some("test_container")).map {
          case res: ContainerCreateResponse =>
            assert(res.id.nonEmpty)
            assert(res.warnings.isEmpty)
        }
      }
    }

    "inspect container" in {
      val handler = pathPrefix("containers" / Segment / "json") { containerIdPart =>
        assert(containerIdPart == containerId)
        get(complete(getFixture("docker/v1.19/container.json")))
      }
      startFakeHttpServer(handler) { port =>
        val dc = new Client("localhost", port)
        dc.containerInspect(containerId).map {
          case res: ContainerBase =>
            val config = RunConfig(
              hostname = Some("hostname"),
              domainName = Some("domainname"),
              user = Some("user"),
              isAttachStdin = true,
              isAttachStdout = true,
              isAttachStderr = true,
              exposedPorts = Set(ExposePort.Tcp(80)),
              publishService = None,
              isTty = true,
              isOpenStdin = true,
              isStdinOnce = true,
              env = List("TEST=test:ttest"),
              command = List("ls"),
              image = Some("ubuntu"),
              volumes = Map.empty,
              volumeDriver = None,
              workingDirectory = Some("/tmp"),
              entrypoint = List("ls"),
              isNetworkDisabled = true,
              macAddress = Some("bb:cc:ff:22:11:11"),
              labels = Map("label1" -> "value"),
              onBuild = List.empty
            )
            val hostConfig = HostConfig(
              binds = List(VolumeBindPath.VolumeHostPath("/tmp", "/tmp", MountMode.rw)),
              links = List(ContainerLink("/nginx", "/test_container/nginx")),
              memory = Some(5.MB),
              memorySwap = Some(5.MB),
              cpuShares = Some(100),
              cpuPeriod = Some(100000),
              cpusetCpus = Some("0,1"),
              cpusetMems = Some("0,1"),
              cpuQuota = Some(10000),
              blockIoWeight = Some(300),
              memorySwappiness = None,
              isOomKillDisable = true,
              portBindings = Map(
                ExposePort.Tcp(80) -> List(PortBinding(80, Some("0.0.0.0")))
              ),
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
            val containerBase = ContainerBase(
              id = containerId,
              createdAt = ZonedDateTime.parse("2015-11-04T18:14:18.619944537Z"),
              path = "ls",
              args = List("ls"),
              state = ContainerState(
                isRunning = false,
                isPaused = false,
                isRestarting = false,
                isOomKilled = false,
                isDead = false,
                pid = None,
                exitCode = 0,
                error = None,
                startedAt = None,
                finishedAt = None
              ),
              image = "a5a467fddcb8848a80942d0191134c925fa16ffa9655c540acd34284f4f6375d",
              networkSettings = NetworkSettings(
                bridge = None,
                endpointId = None,
                gateway = None,
                globalIpv6Address = None,
                globalIpv6PrefixLength = 0,
                hairpinMode = false,
                ipAddress = None,
                ipPrefixLength = 0,
                ipv6Gateway = None,
                linkLocalIpv6Address = None,
                linkLocalIpv6PrefixLength = 0,
                macAddress = None,
                networkId = None,
                ports = Map.empty,
                sandboxKey = None,
                secondaryIpAddresses = List.empty,
                secondaryIpv6Addresses = List.empty
              ),
              resolvConfPath = None,
              hostnamePath = None,
              hostsPath = None,
              logPath = None,
              name = "/test_container",
              restartCount = 0,
              driver = "overlay",
              execDriver = "native-0.2",
              mountLabel = None,
              processLabel = None,
              volumes = Map(
                "/tmp" -> "/tmp",
                "/var/cache/nginx" -> "/var/lib/docker/volumes/e8b234d862623ab56c04f36639a4c5dbb19169d9dbdf13bff3f3a010480fd404/_data"
              ),
              volumesRw = Map(
                "/tmp" -> true,
                "/var/cache/nginx" -> false
              ),
              appArmorProfile = None,
              execIds = List.empty,
              hostConfig = hostConfig,
              config = config
            )
            assert(res == containerBase)
        }
      }
    }

    "get container processes" in {
      val handler = pathPrefix("containers" / Segment / "top") { containerIdPart =>
        assert(containerIdPart == containerId)
        get(complete(getFixture("docker/v1.19/container_processes.json")))
      }
      startFakeHttpServer(handler) { port =>
        val dc = new Client("localhost", port)
        dc.containerProcesses(containerId).map {
          case res: ContainerProcessList =>
            assert(res.processes == List(
              List("root", "1022", "742", "0", "16:33", "?", "00:00:00", "nginx: master process nginx -g daemon off;"),
              List("104", "1027", "1022", "0", "16:33", "?", "00:00:00", "nginx: worker process")
            ))
            assert(res.titles == List("UID", "PID", "PPID", "C", "STIME", "TTY", "TIME", "CMD"))
        }
      }
    }

    "get container logs" in {
      val handler = pathPrefix("containers" / Segment / "logs") { containerIdPart =>
        assert(containerIdPart == containerId)
        get(complete(getFixture("docker/v1.19/logs")))
      }
      startFakeHttpServer(handler) { port =>
        val dc = new Client("localhost", port)
        dc.containerLogs(containerId, Set(StdStream.Out)).flatMap { s =>
          source2String(s).map { logs =>
            assert(logs == "Log line 1\nLog line 2\n")
          }
        }
      }
    }

    "get container logs as stream" in {
      val handler = pathPrefix("containers" / Segment / "logs") { containerIdPart =>
        assert(containerIdPart == containerId)
        get(complete(getFixture("docker/v1.19/logs")))
      }
      startFakeHttpServer(handler) { port =>
        val dc = new Client("localhost", port)
        dc.containerLogsAsStream(containerId, Set(StdStream.Out), idleTimeout = timeout)
          .flatMap { s =>
            source2String(s).map { logs =>
              assert(logs == "Log line 1\nLog line 2\n")
            }
          }
      }
    }

    "get container changes" in {
      val handler = pathPrefix("containers" / Segment / "changes") { containerIdPart =>
        assert(containerIdPart == containerId)
        get(complete(getFixture("docker/v1.19/changes.json")))
      }
      startFakeHttpServer(handler) { port =>
        val dc = new Client("localhost", port)
        dc.containerChanges(containerId).map {
          case changes: ContainerChanges =>
            val fileChanges = ContainerChanges(List(
              FileChange("/root", FileChangeKind.Modified),
              FileChange("/root/.dbshell", FileChangeKind.Added),
              FileChange("/root/.mongorc.js", FileChangeKind.Added),
              FileChange("/tmp", FileChangeKind.Modified),
              FileChange("/tmp/mongodb-27017.sock", FileChangeKind.Added)
            ))
            assert(changes == fileChanges)
        }
      }
    }

    "container export" in {
      val handler = pathPrefix("containers" / Segment / "export") { containerIdPart =>
        assert(containerIdPart == containerId)
        get(complete(getFixture("docker/v1.19/export")))
      }
      startFakeHttpServer(handler) { port =>
        val dc = new Client("localhost", port)
        dc.containerExport(containerId).flatMap { s =>
          source2ByteString(s).map { bs =>
            assert(bs == ByteString("some data"))
          }
        }
      }
    }

    "container stats" in {
      val handler = pathPrefix("containers" / Segment / "stats") { containerIdPart =>
        assert(containerIdPart == containerId)
        get(complete(getFixture("docker/v1.19/stats.json")))
      }
      startFakeHttpServer(handler) { port =>
        val dc = new Client("localhost", port)
        dc.containerStats(containerId).map {
          case stats: ContainerStats =>
            assert(stats.readedAt == ZonedDateTime.parse("2015-11-09T17:13:24.902264505Z"))
        }
      }
    }

    "container stats as stream" in {
      val handler = pathPrefix("containers" / Segment / "stats") { containerIdPart =>
        assert(containerIdPart == containerId)
        get(complete(getFixture("docker/v1.19/stats.json")))
      }
      startFakeHttpServer(handler) { port =>
        val dc = new Client("localhost", port)
        dc.containerStatsAsStream(containerId).flatMap(_.map { stats =>
          assert(stats.readedAt == ZonedDateTime.parse("2015-11-09T17:13:24.902264505Z"))
        }.runWith(Sink.head))
      }
    }

  }
}
