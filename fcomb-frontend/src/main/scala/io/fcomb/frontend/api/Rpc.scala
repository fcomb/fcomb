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
import org.scalajs.dom.ext.Ajax
import org.scalajs.dom.window
import scala.concurrent.{ExecutionContext, Future}
import upickle.default.{Reader, Writer, write => writeJs, readJs}
import upickle.json.{read => readToJs}

sealed trait RpcMethod

object RpcMethod {
  final case object GET    extends RpcMethod
  final case object HEAD   extends RpcMethod
  final case object POST   extends RpcMethod
  final case object PUT    extends RpcMethod
  final case object DELETE extends RpcMethod
}

object Rpc {
  private val contentTypeHeader = ("Content-Type" -> "application/json")

  def call[T, U](method: RpcMethod,
                 url: String,
                 req: T,
                 headers: Map[String, String] = Map.empty,
                 timeout: Int = 0)(
      implicit ec: ExecutionContext, wt: Writer[T], ru: Reader[U]): Future[Xor[String, U]] = {
    val hm = headers + contentTypeHeader
    Ajax
      .apply(method.toString, url, writeJs(req), timeout, hm, withCredentials = false, "")
      .map(res => Xor.right(readJs[U](readToJs(res.responseText))))
      .recover {
        case e =>
          val msg = s"${e.toString}: ${e.getMessage}"
          window.console.error(msg)
          Xor.left(msg)
      }
  }
}
