package io.fcomb.docker.distribution.api.services

import akka.http.scaladsl.model.headers._

package object headers {
  case class `Docker-Distribution-Api-Version`(version: String) extends CustomHeader {
    def renderInRequests = false
    def renderInResponses = true
    def name: String = "Docker-Distribution-Api-Version"
    def value: String = s"registry/$version"
  }
}
