/*
 * Copyright 2016 fcomb. <https://fcomb.io>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.fcomb.frontend.api

import cats.data.Xor
import io.circe.scalajs.decodeJs
import io.circe.syntax._
import io.circe.{Decoder, Encoder}
import io.fcomb.frontend.dispatcher.actions.LogOut
import io.fcomb.frontend.dispatcher.AppCircuit
import io.fcomb.models.errors.{Error, Errors}
import io.fcomb.json.models.errors.Formats.decodeErrors
import org.scalajs.dom.ext.Ajax
import org.scalajs.dom.ext.AjaxException
import org.scalajs.dom.window
import scala.concurrent.{ExecutionContext, Future}
import scala.scalajs.js.{JSON, URIUtils}

sealed trait RpcMethod

object RpcMethod {
  final case object GET    extends RpcMethod
  final case object HEAD   extends RpcMethod
  final case object POST   extends RpcMethod
  final case object PUT    extends RpcMethod
  final case object DELETE extends RpcMethod
}

object Rpc {
  def callWith[T: Encoder, U: Decoder](
      method: RpcMethod,
      url: String,
      req: T,
      queryParams: Map[String, String] = Map.empty,
      headers: Map[String, String] = Map.empty,
      timeout: Int = 0)(implicit ec: ExecutionContext): Future[Xor[Seq[Error], U]] = {
    val hm        = if (headers.isEmpty) defaultHeaders else headers ++ defaultHeaders
    val reqBody   = req.asJson.noSpaces
    val urlParams = queryParams.map { case (k, v) => s"$k=$v" }.mkString("&")
    val targetUrl = URIUtils.encodeURI(s"$url?$urlParams")
    Ajax
      .apply(method.toString, targetUrl, reqBody, timeout, hm, withCredentials = false, "")
      .map { res =>
        if (res.status == 401) unauthorized()
        else decode[U](res.responseText)
      }
      .recover {
        case AjaxException(res) =>
          if (res.status == 401) unauthorized()
          else decode[Errors](res.responseText).flatMap(res => Xor.Left(res.errors))
        case e =>
          window.console.error(s"${e.toString}: ${e.getMessage}")
          Xor.Left(Seq(Errors.unknown))
      }
  }

  private def decode[U](responseText: String)(implicit dec: Decoder[U]): Xor[Seq[Error], U] = {
    val body = if (responseText.nonEmpty) responseText else "null"
    val json = JSON.parse(body)
    decodeJs[U](json).leftMap(_ => Seq(Errors.deserialization()))
  }

  def call[U](method: RpcMethod,
              url: String,
              queryParams: Map[String, String] = Map.empty,
              headers: Map[String, String] = Map.empty,
              timeout: Int = 0)(implicit ec: ExecutionContext,
                                decoder: Decoder[U]): Future[Xor[Seq[Error], U]] =
    callWith[Unit, U](method, url, (), queryParams, headers, timeout)

  private def unauthorized[U](): Xor[Seq[Error], U] = {
    AppCircuit.dispatch(LogOut)
    Xor.Left(Seq(Errors.unauthorized))
  }

  private val contentTypeHeader = Map("Content-Type" -> "application/json")

  private def defaultHeaders: Map[String, String] =
    AppCircuit.session match {
      case Some(sessionToken) =>
        contentTypeHeader + (("Authorization", s"Bearer $sessionToken"))
      case None => contentTypeHeader
    }
}
