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

package io.fcomb.frontend.components.repository

import chandu0101.scalajs.react.components.Implicits._
import chandu0101.scalajs.react.components.materialui._
import io.fcomb.frontend.components.Implicits._
import io.fcomb.frontend.components.LayoutComponent
import io.fcomb.frontend.styles.App
import io.fcomb.models.docker.distribution.ImageVisibilityKind
import japgolly.scalajs.react.vdom.prefix_<^._
import japgolly.scalajs.react._

object RepositoryForm {
  def renderVisiblity(visibilityKind: ImageVisibilityKind,
                      isFormDisabled: Boolean,
                      updateVisibilityKind: (ReactEventI, String) => Callback) = {
    val label = <.label(
      ^.`for` := "visibilityKind",
      "Repository visibility for others: it can be public to everyone to read and pull or private accessible only to the owner.")
    <.div(^.`class` := "row",
          ^.style := App.paddingTopStyle,
          ^.key := "visibilityRow",
          <.div(^.`class` := "col-xs-6",
                MuiRadioButtonGroup(name = "visibilityKind",
                                    defaultSelected = visibilityKind.value,
                                    onChange = updateVisibilityKind)(
                  MuiRadioButton(
                    key = "public",
                    value = ImageVisibilityKind.Public.value,
                    label = "Public",
                    disabled = isFormDisabled
                  )(),
                  MuiRadioButton(
                    style = App.paddingTopStyle,
                    key = "private",
                    value = ImageVisibilityKind.Private.value,
                    label = "Private",
                    disabled = isFormDisabled
                  )()
                )),
          <.div(LayoutComponent.helpBlockClass, label))
  }

  def renderDescription(description: String,
                        errors: Map[String, String],
                        isFormDisabled: Boolean,
                        updateDescription: ReactEventI => Callback) = {
    val link = <.a(LayoutComponent.linkAsTextStyle,
                   ^.href := "https://daringfireball.net/projects/markdown/syntax",
                   ^.target := "_blank",
                   "Markdown")
    <.div(
      ^.`class` := "row",
      ^.key := "descriptionRow",
      <.div(^.`class` := "col-xs-6",
            MuiTextField(floatingLabelText = "Description",
                         id = "description",
                         name = "description",
                         multiLine = true,
                         fullWidth = true,
                         rowsMax = 15,
                         disabled = isFormDisabled,
                         errorText = errors.get("description"),
                         value = description,
                         onChange = updateDescription)()),
      <.div(LayoutComponent.helpBlockClass,
            ^.style := App.helpBlockStyle,
            <.label(^.`for` := "description", "You can describe this repository in ", link, ".")))
  }
}
