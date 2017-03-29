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

import chandu0101.scalajs.react.components.Implicits._
import chandu0101.scalajs.react.components.materialui._
import io.fcomb.frontend.components.Implicits._
import io.fcomb.frontend.styles.App
import io.fcomb.models.common.{Enum, EnumItem}
import japgolly.scalajs.react.vdom.prefix_<^._
import japgolly.scalajs.react._
import scala.scalajs.js

object Form {
  def row(component: ReactNode, helpBlock: ReactNode, key: String) =
    <.div(^.`class` := "row",
          ^.key := key,
          <.div(^.`class` := "col-xs-6", component),
          <.div(LayoutComponent.helpBlockClass, ^.style := App.helpBlockStyle, helpBlock))

  def textField(value: String,
                key: String,
                label: String,
                errors: Map[String, String],
                isDisabled: Boolean,
                onChange: String => Callback,
                `type`: String = "text",
                fullWidth: Boolean = true,
                multiLine: js.UndefOr[Boolean] = js.undefined,
                rowsMax: js.UndefOr[Int] = js.undefined) =
    MuiTextField(
      floatingLabelText = label,
      `type` = `type`,
      id = key,
      name = key,
      disabled = isDisabled,
      errorText = errors.get(key),
      fullWidth = fullWidth,
      multiLine = multiLine,
      rowsMax = rowsMax,
      value = value,
      onChange = (e: ReactEvent, v: String) => onChange(v)
    )()

  def selectEnum[E <: EnumItem](enum: Enum[E],
                                value: E,
                                key: String,
                                label: String,
                                errors: Map[String, String],
                                isDisabled: Boolean,
                                onChange: E => Callback,
                                fullWidth: Boolean = true) = {
    val items = enum.values.map(i =>
      MuiMenuItem[E](key = i.value, value = i, primaryText = i.entryName.capitalize)())
    MuiSelectField[E](
      id = key,
      floatingLabelText = label,
      disabled = isDisabled,
      errorText = errors.get(key),
      value = value,
      fullWidth = fullWidth,
      onChange = (e: TouchTapEvent, idx: Int, item: E) => onChange(item)
    )(items)
  }
}
