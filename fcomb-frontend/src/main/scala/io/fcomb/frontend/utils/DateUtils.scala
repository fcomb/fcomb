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

package io.fcomb.frontend.utils

import scala.scalajs.js.Date

object DateUtils {
  val minute = 60
  val hour   = minute * 60
  val day    = hour * 24
  val week   = day * 7
  val month  = week * 4
  val year   = month * 12

  def distance(from: Date, to: Date): String = {
    val secondsAgo = (to.getTime() - from.getTime()).toInt / 1000
    if (secondsAgo < minute) "less than a minute ago"
    else if (secondsAgo < hour) plural(secondsAgo / minute, "minute")
    else if (secondsAgo < day) plural(secondsAgo / hour, "hour")
    else if (secondsAgo < week) plural(secondsAgo / day, "day")
    else if (secondsAgo < month) plural(secondsAgo / week, "week")
    else if (secondsAgo < year) plural(secondsAgo / month, "month")
    else plural(secondsAgo / year, "year")
  }

  private def plural(n: Int, noun: String) = {
    val postfix = if (n == 1) noun else noun + "s"
    s"$n $postfix ago"
  }
}
