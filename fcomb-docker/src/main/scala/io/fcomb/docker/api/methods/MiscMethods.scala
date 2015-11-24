package io.fcomb.docker.api.methods

import akka.http.scaladsl.model.headers.RawHeader
import scala.collection.immutable
import spray.json._
import org.apache.commons.codec.binary.Base64
import java.time.ZonedDateTime

object MiscMethods {
  final case class IndexInfo(
    name: String,
    mirrors: List[String],
    isSecure: Boolean,
    isOfficial: Boolean
  )

  final case class ServiceConfig(
    insecureRegistryCidrs: List[String],
    indexConfigs: Map[String, IndexInfo]
  )

  final case class Information(
    id: String,
    continers: Int,
    images: Int,
    driver: String,
    driverStatus: List[List[String]],
    isMemoryLimit: Boolean,
    isSwapLimit: Boolean,
    isCpuCfsPeriod: Boolean,
    isCpuCfsQuota: Boolean,
    isIpv4Forwarding: Boolean,
    isBridgeNfIptables: Boolean,
    isBridgeNfIp6tables: Boolean,
    isDebug: Boolean,
    fileDescriptors: Int,
    isOomKillDisable: Boolean,
    goroutines: Int,
    systemTime: ZonedDateTime,
    executionDriver: String,
    loggingDriver: Option[String],
    eventsListeners: Int,
    kernelVersion: String,
    operatingSystem: String,
    indexServerAddress: String,
    registryConfig: Option[ServiceConfig],
    initSha1: String,
    initPath: String,
    cpus: Int,
    memory: Long,
    dockerRootDir: String,
    httpProxy: Option[String],
    httpsProxy: Option[String],
    noProxy: Option[String],
    name: Option[String],
    labels: Map[String, String],
    isExperimentalBuild: Boolean
  ) extends DockerApiResponse

  final case class ExecStartCheck(
    isDetach: Boolean,
    isTty: Boolean
  )

  final case class Version(
    version: String,
    apiVersion: String,
    gitCommit: String,
    goVersion: String,
    os: String,
    arch: String,
    kernelVersion: Option[String],
    experimental: Boolean,
    buildTime: Option[String]
  )

  final case class AuthConfig(
    username: String,
    password: String,
    email: Option[String],
    serverAddress: String
  ) extends DockerApiRequest

  object AuthConfig {
    import io.fcomb.docker.api.json.MiscMethodsFormat.authConfigFormat

    private val emptyConfig = Base64.encodeBase64String(JsObject().compactPrint.getBytes)

    def mapToHeaders(configOpt: Option[AuthConfig]) = {
      val value = configOpt match {
        case Some(config) => mapToJsonAsBase64(config)
        case None => emptyConfig
      }
      immutable.Seq(RawHeader("X-Registry-Auth", value))
    }
  }

}
