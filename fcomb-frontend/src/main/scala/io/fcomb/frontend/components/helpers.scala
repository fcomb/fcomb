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

package io.fcomb.frontend.components

import io.fcomb.models.errors.ErrorMessage
import japgolly.scalajs.react.ReactNode
import scala.language.implicitConversions
import scala.scalajs.js.JSConverters._
import scala.scalajs.js.UndefOr

package object helpers {
  def foldErrors(errors: Seq[ErrorMessage]): Map[String, String] = {
    errors.foldLeft(Map.empty[String, String]) {
      case (m, err) =>
        val column = err.param.getOrElse("_")
        val msg    = err.message
        val value  = m.get(column).map(v => s"$v\n$msg").getOrElse(msg)
        m + ((column, value))
    }
  }

  object Implicits {
    implicit def optString2ReactNode(opt: Option[String]): UndefOr[ReactNode] =
      opt.map(_.asInstanceOf[ReactNode]).orUndefined
  }
}
