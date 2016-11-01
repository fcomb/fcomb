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

package io.fcomb.frontend.components.organization

import chandu0101.scalajs.react.components.Implicits._
import chandu0101.scalajs.react.components.materialui._
import io.fcomb.frontend.components.Implicits._
import io.fcomb.frontend.components.LayoutComponent
import io.fcomb.frontend.styles.App
import io.fcomb.models.acl.Role
import japgolly.scalajs.react.vdom.prefix_<^._
import japgolly.scalajs.react._

object GroupForm {
  final case class FormState(name: String, role: Role, errors: Map[String, String])

  def defaultFormState = FormState("", Role.Member, Map.empty)

  def renderFormName(state: FormState, isDisabled: Boolean, cb: ReactEventI => Callback) =
    <.div(^.`class` := "row",
          ^.key := "name",
          <.div(^.`class` := "col-xs-6",
                MuiTextField(floatingLabelText = "Name",
                             id = "name",
                             name = "name",
                             disabled = isDisabled,
                             errorText = state.errors.get("name"),
                             fullWidth = true,
                             value = state.name,
                             onChange = cb)()),
          <.div(LayoutComponent.helpBlockClass,
                ^.style := App.helpBlockStyle,
                <.label(^.`for` := "name", "Unique group name.")))

  lazy val roleHelpBlock =
    <.div(
      "What can a group user do with repositories:",
      <.ul(<.li(<.strong("Member"), " - pull only;"),
           <.li(<.strong("Creator"), " - create, pull and push;"),
           <.li(<.strong("Admin"), " - create, pull, push and manage groups and permissions.")))

  lazy val roles = Role.values.map(r =>
    MuiMenuItem[Role](key = r.value, value = r, primaryText = r.entryName.capitalize)())

  def renderFormRole(state: FormState,
                     isDisabled: Boolean,
                     cb: (ReactEventI, Int, Role) => Callback) =
    <.div(^.`class` := "row",
          ^.key := "role",
          <.div(^.`class` := "col-xs-6",
                MuiSelectField[Role](id = "role",
                                     floatingLabelText = "Role",
                                     disabled = isDisabled,
                                     errorText = state.errors.get("role"),
                                     value = state.role,
                                     fullWidth = true,
                                     onChange = cb)(roles)),
          <.div(LayoutComponent.helpBlockClass,
                ^.style := App.helpBlockStyle,
                <.label(^.`for` := "role", roleHelpBlock)))
}
