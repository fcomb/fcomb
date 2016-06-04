package io.fcomb.server

import akka.http.scaladsl.model.headers._

object headers {
  final case class `X-Content-Type-Options`(options: String) extends CustomHeader {
    def renderInRequests = false
    def renderInResponses = true
    def name: String = "X-Content-Type-Options"
    def value: String = options
  }

  final case class `X-Frame-Options`(options: String) extends CustomHeader {
    def renderInRequests = false
    def renderInResponses = true
    def name: String = "X-Frame-Options"
    def value: String = options
  }

  final case class `X-XSS-Protection`(options: String) extends CustomHeader {
    def renderInRequests = false
    def renderInResponses = true
    def name: String = "X-XSS-Protection"
    def value: String = options
  }
}
