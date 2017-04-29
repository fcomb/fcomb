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

package io.fcomb.frontend.utils

object UnitUtils {
  val kb = 1024.0
  val mb = kb * kb
  val gb = mb * kb
  val tb = gb * kb

  def sizeInBytes(n: Long): String =
    if (n < kb) s"${format(n.toDouble)} B"
    else if (n < mb) s"${format(n / kb)} KB"
    else if (n < gb) s"${format(n / mb)} MB"
    else if (n < tb) s"${format(n / gb)} GB"
    else s"${format(n / tb)} TB"

  private def format(n: Double) = {
    val s = "%.1f".format(n)
    if (s.endsWith(".0")) s.dropRight(2)
    else s
  }
}
