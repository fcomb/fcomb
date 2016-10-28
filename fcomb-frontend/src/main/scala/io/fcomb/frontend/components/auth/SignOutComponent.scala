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

package io.fcomb.frontend.components.auth

import io.fcomb.frontend.Route
import io.fcomb.frontend.api.{Resource, Rpc, RpcMethod}
import io.fcomb.frontend.dispatcher.AppCircuit
import io.fcomb.frontend.dispatcher.actions.LogOut
import japgolly.scalajs.react._
import japgolly.scalajs.react.extra.router.RouterCtl
import japgolly.scalajs.react.vdom.prefix_<^._
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue

object SignOutComponent {
  final class Backend($ : BackendScope[RouterCtl[Route], Unit]) {
    def render(ctl: RouterCtl[Route]) =
      <.h1("sign out...")
  }

  private val component = ReactComponentB[RouterCtl[Route]]("SignOut")
    .renderBackend[Backend]
    .componentDidMount { $ ⇒
      val cb = AppCircuit.dispatchCB(LogOut) >> $.props.set(Route.SignIn).delayMs(1).void
      AppCircuit.session match {
        case Some(sessionToken) =>
          Callback.future {
            Rpc.call[Unit](RpcMethod.DELETE, Resource.sessions).map(_ => cb).recover {
              case _ => cb
            }
          }
        case None => cb
      }
    }
    .build

  def apply(ctl: RouterCtl[Route]) = component(ctl)
}
