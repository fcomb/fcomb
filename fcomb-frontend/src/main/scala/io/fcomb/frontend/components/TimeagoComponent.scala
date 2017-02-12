/*
 * Copyright 2017 fcomb. <https://fcomb.io>
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

package io.fcomb.frontend.components

import io.fcomb.frontend.utils.DateUtils
import japgolly.scalajs.react._
import japgolly.scalajs.react.vdom.prefix_<^._
import scala.concurrent.duration._
import scala.scalajs.js.Date
import scala.scalajs.js.timers

object TimeAgoComponent {
  final case class State(distance: String)

  final class Backend($ : BackendScope[Date, State]) {
    private var timer: timers.SetIntervalHandle = _

    def updateDistance(): Callback =
      for {
        date  <- $.props
        state <- $.state
        _ <- {
          val newDistance = DateUtils.distance(date, new Date())
          if (state.distance == newDistance) Callback.empty
          else $.modState(_.copy(distance = newDistance))
        }
      } yield ()

    def startTimer(): Callback =
      stopTimer() >>
        CallbackTo {
          val interval = timers.setInterval(1.second)(updateDistance().runNow())
          timer = interval
        }

    def stopTimer(): Callback =
      CallbackTo {
        if (timer != null) timers.clearInterval(timer)
      }

    def render(date: Date, state: State) =
      <.span(^.title := date.toISOString(), state.distance)
  }

  private val component = ReactComponentB[Date]("TimeAgo")
    .initialState(State(""))
    .renderBackend[Backend]
    .componentDidMount(_.backend.updateDistance())
    .componentDidMount(_.backend.startTimer())
    .componentWillUnmount(_.backend.stopTimer())
    .build

  def apply(date: String) =
    component(new Date(date))
}
