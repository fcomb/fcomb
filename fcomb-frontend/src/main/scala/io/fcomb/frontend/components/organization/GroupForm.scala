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

package io.fcomb.frontend.components.organization

import io.fcomb.frontend.components.Form
import io.fcomb.models.acl.Role
import japgolly.scalajs.react.vdom.prefix_<^._
import japgolly.scalajs.react._

object GroupForm {
  final case class FormState(name: String, role: Role, errors: Map[String, String])

  def defaultFormState = FormState("", Role.Member, Map.empty)

  def renderFormName(state: FormState, isDisabled: Boolean, onChange: String => Callback) =
    Form.row(
      Form.textField(label = "Name",
                     key = "name",
                     isDisabled = isDisabled,
                     errors = state.errors,
                     value = state.name,
                     onChange = onChange),
      <.label(^.`for` := "name", "Unique group name."),
      "name"
    )

  lazy val roleHelpBlock =
    <.div(
      "What can a group user do with repositories:",
      <.ul(
        <.li(<.strong("Member"), " - pull only;"),
        <.li(<.strong("Creator"), " - create, pull and push;"),
        <.li(<.strong("Admin"), " - create, pull, push and manage groups and permissions.")
      )
    )

  def renderFormRole(state: FormState, isDisabled: Boolean, onChange: Role => Callback) =
    Form.row(
      Form.selectEnum(enum = Role,
                      value = state.role,
                      key = "role",
                      label = "Role",
                      errors = state.errors,
                      isDisabled = isDisabled,
                      onChange = onChange),
      <.label(^.`for` := "role", roleHelpBlock),
      "role"
    )
}
