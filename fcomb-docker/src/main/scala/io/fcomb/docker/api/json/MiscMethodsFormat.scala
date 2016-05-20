package io.fcomb.docker.api.json

import io.fcomb.docker.api.methods.MiscMethods._
import spray.json._
import spray.json.DefaultJsonProtocol.{ listFormat ⇒ _, _ }
import io.fcomb.json._
import java.time.{ LocalDateTime, ZonedDateTime }

private[api] object MiscMethodsFormat {
  implicit val indexInformationFormat = jsonFormat(
    IndexInfo, "Name", "Mirrors", "Secure", "Official"
  )

  implicit val serviceConfigFormat = jsonFormat(
    ServiceConfig, "InsecureRegistryCIDRs", "IndexConfigs"
  )

  implicit object InformationFormat extends RootJsonReader[Information] {
    def read(v: JsValue) = v match {
      case obj: JsObject ⇒
        Information(
          id = obj.get[String]("ID"),
          continers =
            obj.get[Int]("Containers"),
          images =
            obj.get[Int]("Images"),
          driver = obj.get[String]("Driver"),
          driverStatus =
            obj.getList[List[String]]("DriverStatus"),
          isMemoryLimit = obj.get[Boolean]("MemoryLimit"),
          isSwapLimit =
            obj.get[Boolean]("SwapLimit"),
          isCpuCfsPeriod = obj.getOrElse[Boolean]("CpuCfsPeriod", false),
          isCpuCfsQuota = obj.getOrElse[Boolean]("CpuCfsQuota", false),
          isIpv4Forwarding = obj.get[Boolean]("IPv4Forwarding"),
          isBridgeNfIptables =
            obj.getOrElse[Boolean]("BridgeNfIptables", false),
          isBridgeNfIp6tables = obj
            .getOrElse[Boolean]("BridgeNfIp6tables", false),
          isDebug =
            obj.get[Boolean]("Debug"),
          fileDescriptors = obj.get[Int]("NFd"),
          isOomKillDisable = obj.getOrElse[Boolean]("OomKillDisable", true),
          goroutines = obj.get[Int]("NGoroutines"),
          systemTime = obj.get[ZonedDateTime]("SystemTime"),
          executionDriver = obj.get[String]("ExecutionDriver"),
          loggingDriver = obj.getOpt[String]("LoggingDriver"),
          eventsListeners = obj.get[Int]("NEventsListener"),
          kernelVersion =
            obj.get[String]("KernelVersion"),
          operatingSystem = obj.get[String]("OperatingSystem"),
          registryConfig = obj.getOpt[ServiceConfig]("RegistryConfig"),
          indexServerAddress = obj.get[String]("IndexServerAddress"),
          initSha1 =
            obj.get[String]("InitSha1"),
          initPath =
            obj.get[String]("InitPath"),
          cpus =
            obj.get[Int]("NCPU"),
          memory = obj.get[Long]("MemTotal"),
          dockerRootDir = obj.get[String]("DockerRootDir"),
          httpProxy = obj.getOpt[String]("HttpProxy"),
          httpsProxy = obj.getOpt[String]("HttpsProxy"),
          noProxy = obj.getOpt[String]("NoProxy"),
          name = obj.getOpt[String]("Name"),
          labels = obj.get[Map[String, String]]("Labels"),
          isExperimentalBuild =
            obj.getOrElse[Boolean]("ExperimentalBuild", false)
        )
      case x ⇒ deserializationError(s"Expected JsObject, but got $x")
    }
  }

  implicit object VersionFormat extends RootJsonReader[Version] {
    def read(v: JsValue) = v match {
      case obj: JsObject ⇒
        Version(
          version = obj.get[String]("Version"),
          apiVersion = obj.get[String]("ApiVersion"),
          gitCommit = obj.get[String]("GitCommit"),
          goVersion = obj.get[String]("GoVersion"),
          os = obj.get[String]("Os"),
          arch = obj.get[String]("Arch"),
          kernelVersion = obj.getOpt[String]("KernelVersion"),
          experimental = obj.getOrElse[Boolean]("Experimental", false),
          buildTime = obj.getOpt[String]("BuildTime")
        )
      case x ⇒ deserializationError(s"Expected JsObject, but got $x")
    }
  }

  implicit val authConfigFormat = jsonFormat(
    AuthConfig.apply, "username", "password", "email", "serveraddress"
  )

  implicit object EventMessageProgressFormat
      extends RootJsonFormat[EventMessageProgress] {
    def write(m: EventMessageProgress) = throw new NotImplementedError

    def read(v: JsValue) = v match {
      case obj: JsObject ⇒
        val total = obj.getOpt[Long]("total") match {
          case Some(n) if n <= 0 ⇒ None
          case t                 ⇒ t
        }
        EventMessageProgress(
          current = obj.get[Long]("current"),
          total = total,
          startedAt = obj.getOpt[ZonedDateTime]("start")
        )
      case x ⇒ deserializationError(s"Expected JsObject, but got $x")
    }
  }

  implicit object EventMessageErrorFormat
      extends RootJsonFormat[EventMessageError] {
    def write(m: EventMessageError) = throw new NotImplementedError

    def read(v: JsValue) = v match {
      case obj: JsObject ⇒
        EventMessageError(
          code = obj.getOpt[Int]("code"),
          message = obj.get[String]("message")
        )
      case x ⇒ deserializationError(s"Expected JsObject, but got $x")
    }
  }

  implicit object EventMessageFormat extends RootJsonReader[EventMessage] {
    def read(v: JsValue) = v match {
      case obj: JsObject ⇒
        val progressDetail = obj.fields.get("progressDetail").flatMap {
          pdObj ⇒
            if (pdObj.asJsObject.fields.nonEmpty)
              Some(pdObj.convertTo[EventMessageProgress])
            else None
        }
        EventMessage(
          stream = obj.getOpt[String]("stream"),
          status = obj.getOpt[String]("status"),
          progressDetail = progressDetail,
          progressMessage = obj.getOpt[String]("progress"),
          id = obj.getOpt[String]("id"),
          from = obj.getOpt[String]("from"),
          timeAt = obj.getOpt[ZonedDateTime]("time"),
          errorDetail = obj.getOpt[EventMessageError]("errorDetail"),
          errorMessage = obj.getOpt[String]("error")
        )
      case x ⇒ deserializationError(s"Expected JsObject, but got $x")
    }
  }

  implicit object EventKindMessageFormat
      extends RootJsonReader[EventKindMessage] {
    def read(v: JsValue) = v match {
      case obj: JsObject ⇒
        val event = DockerEvent.fromString(obj.get[String]("status"))
        EventKindMessage(
          event = event,
          id = obj.get[String]("id"),
          from = obj.getOpt[String]("from"),
          timeAt = obj.get[ZonedDateTime]("time")
        )
      case x ⇒ deserializationError(s"Expected JsObject, but got $x")
    }
  }
}
