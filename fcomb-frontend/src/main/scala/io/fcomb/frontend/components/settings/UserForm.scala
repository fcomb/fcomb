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

package io.fcomb.frontend.components.settings

import chandu0101.scalajs.react.components.Implicits._
import chandu0101.scalajs.react.components.materialui._
import io.fcomb.frontend.components.Form
import io.fcomb.frontend.DashboardRoute
import io.fcomb.frontend.styles.App
import io.fcomb.models.UserRole
import japgolly.scalajs.react.extra.router.RouterCtl
import japgolly.scalajs.react.vdom.prefix_<^._
import japgolly.scalajs.react._

object UserForm {
  def renderFormUsername(value: String,
                         errors: Map[String, String],
                         isDisabled: Boolean,
                         onChange: String => Callback) =
    Form.row(
      Form.textField(label = "Username",
                     key = "username",
                     isDisabled = isDisabled,
                     errors = errors,
                     value = value,
                     onChange = onChange),
      <.label(^.`for` := "username", "Unique username."),
      "username"
    )

  def renderFormEmail(value: String,
                      errors: Map[String, String],
                      isDisabled: Boolean,
                      onChange: String => Callback) =
    Form.row(
      Form.textField(label = "Email",
                     key = "email",
                     isDisabled = isDisabled,
                     errors = errors,
                     value = value,
                     onChange = onChange,
                     `type` = "email"),
      <.label(^.`for` := "email", "Unique email."),
      "email"
    )

  def renderFormFullName(value: String,
                         errors: Map[String, String],
                         isDisabled: Boolean,
                         onChange: String => Callback) =
    Form.row(
      Form.textField(label = "Full name (optional)",
                     key = "fullName",
                     isDisabled = isDisabled,
                     errors = errors,
                     value = value,
                     onChange = onChange),
      <.label(^.`for` := "fullName", ""),
      "fullName"
    )

  def renderFormPassword(value: String,
                         errors: Map[String, String],
                         isDisabled: Boolean,
                         onChange: String => Callback) =
    Form.row(
      Form.textField(label = "Password",
                     key = "password",
                     isDisabled = isDisabled,
                     errors = errors,
                     value = value,
                     onChange = onChange,
                     `type` = "password"),
      <.label(^.`for` := "password",
              "Use at least one lowercase letter, one numeral, and seven characters."),
      "password"
    )

  lazy val roleHelpBlock =
    <.div(
      "What can an user do:",
      <.ul(<.li(<.strong("User"), " - manage only own resources;"),
           <.li(<.strong("Admin"), " - manage all users and global settings."))
    )

  def renderFormRole(role: UserRole,
                     errors: Map[String, String],
                     isDisabled: Boolean,
                     onChange: UserRole => Callback) =
    Form.row(
      Form.selectEnum(enum = UserRole,
                      value = role,
                      key = "role",
                      label = "Role",
                      errors = errors,
                      isDisabled = isDisabled,
                      onChange = onChange),
      <.label(^.`for` := "role", roleHelpBlock),
      "role"
    )

  def renderFormButtons(ctl: RouterCtl[DashboardRoute], submitTitle: String, isDisabled: Boolean) =
    <.div(
      ^.`class` := "row",
      ^.style := App.paddingTopStyle,
      ^.key := "actionsRow",
      <.div(
        ^.`class` := "col-xs-12",
        MuiRaisedButton(`type` = "button",
                        primary = false,
                        label = "Cancel",
                        style = App.cancelStyle,
                        disabled = isDisabled,
                        onTouchTap = cancel(ctl) _,
                        key = "cancel")(),
        MuiRaisedButton(`type` = "submit",
                        primary = true,
                        label = submitTitle,
                        disabled = isDisabled,
                        key = "sybmit")()
      )
    )

  def cancel(ctl: RouterCtl[DashboardRoute])(e: ReactEventH): Callback =
    e.preventDefaultCB >> ctl.set(DashboardRoute.Users)
}
