package io.fcomb.docker.distribution.api.services

import akka.http.scaladsl.model.headers._
import java.util.UUID

package object headers {
  final case class `Docker-Distribution-Api-Version`(version: String) extends CustomHeader {
    def renderInRequests = false
    def renderInResponses = true
    def name: String = "Docker-Distribution-Api-Version"
    def value: String = s"registry/$version"
  }

  final case class `Docker-Upload-Uuid`(uuid: UUID) extends CustomHeader {
    def renderInRequests = false
    def renderInResponses = true
    def name: String = "Docker-Upload-Uuid"
    def value: String = uuid.toString
  }

  final case class `Docker-Content-Digest`(schema: String, digest: String) extends CustomHeader {
    def renderInRequests = false
    def renderInResponses = true
    def name: String = "Docker-Content-Digest"
    def value: String = s"$schema:$digest"
  }
}
