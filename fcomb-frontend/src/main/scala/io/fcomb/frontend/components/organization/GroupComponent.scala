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

import cats.data.Xor
import chandu0101.scalajs.react.components.Implicits._
import chandu0101.scalajs.react.components.materialui._
import io.fcomb.frontend.api.Rpc
import io.fcomb.frontend.components.BreadcrumbsComponent
import io.fcomb.frontend.components.Helpers._
import io.fcomb.frontend.components.Implicits._
import io.fcomb.frontend.DashboardRoute
import io.fcomb.frontend.styles.App
import io.fcomb.rpc.{OrganizationGroupResponse, UserProfileResponse}
import japgolly.scalajs.react.extra.router.RouterCtl
import japgolly.scalajs.react.vdom.prefix_<^._
import japgolly.scalajs.react._
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import scalacss.ScalaCssReact._

object GroupComponent {
  final case class Props(ctl: RouterCtl[DashboardRoute], slug: String, group: String)
  final case class FormState(username: String, errors: Map[String, String], isDisabled: Boolean)
  final case class State(group: Option[OrganizationGroupResponse],
                         members: Seq[UserProfileResponse],
                         form: FormState)

  private def defaultFormState =
    FormState("", Map.empty, false)

  final class Backend($ : BackendScope[Props, State]) {
    def getGroup(slug: String, group: String) =
      Callback.future(Rpc.getOrgaizationGroup(slug, group).map {
        case Xor.Right(group) => $.modState(_.copy(group = Some(group)))
        case Xor.Left(e)      => Callback.warn(e)
      })

    def getMembers(slug: String, group: String) =
      Callback.future(Rpc.getOrgaizationGroupMembers(slug, group).map {
        case Xor.Right(pd) => $.modState(_.copy(members = pd.data))
        case Xor.Left(e)   => Callback.warn(e)
      })

    def getGroupWithMembers(slug: String, group: String): Callback =
      for {
        _ <- getGroup(slug, group)
        _ <- getMembers(slug, group)
      } yield ()

    def deleteMember(slug: String, group: String, username: String)(e: ReactEventI) =
      e.preventDefaultCB >>
        Callback.future(Rpc.deleteOrganizationGroupMember(slug, group, username).map {
          case Xor.Right(_) => getMembers(slug, group)
          case Xor.Left(e)  => ??? // TODO
        })

    def renderMember(slug: String, group: String, member: UserProfileResponse) =
      <.tr(<.td(member.username),
           <.td(member.email),
           <.td(
             <.button(^.`type` := "button",
                      ^.onClick ==> deleteMember(slug, group, member.username),
                      "Delete")))

    def renderMembers(slug: String, group: String, members: Seq[UserProfileResponse]) =
      if (members.isEmpty) <.span("No members. Create one!")
      else
        <.table(<.thead(<.tr(<.th("Username"), <.th("Email"), <.th())),
                <.tbody(members.map(renderMember(slug, group, _))))

    def updateFormDisabled(isDisabled: Boolean): Callback =
      $.modState(s => s.copy(form = s.form.copy(isDisabled = isDisabled)))

    def add(props: Props): Callback =
      $.state.flatMap { state =>
        val fs = state.form
        if (fs.isDisabled) Callback.empty
        else {
          $.setState(state.copy(form = fs.copy(isDisabled = true))) >>
            Callback.future(
              Rpc.upsertOrganizationGroupMember(props.slug, props.group, fs.username).map {
                case Xor.Right(_) =>
                  $.modState(_.copy(form = defaultFormState)) >>
                    getMembers(props.slug, props.group)
                case Xor.Left(errs) =>
                  $.setState(state.copy(
                    form = state.form.copy(isDisabled = false, errors = foldErrors(errs))))
              })
        }
      }

    def handleOnSubmit(props: Props)(e: ReactEventH): Callback =
      e.preventDefaultCB >> add(props)

    def updateUsername(e: ReactEventI): Callback = {
      val value = e.target.value
      $.modState(s => s.copy(form = s.form.copy(username = value)))
    }

    def renderForm(props: Props, state: State) = {
      val form = state.form
      <.form(^.onSubmit ==> handleOnSubmit(props),
             ^.disabled := form.isDisabled,
             <.div(^.display.flex,
                   ^.flexDirection.column,
                   MuiTextField(floatingLabelText = "Username",
                                id = "username",
                                name = "username",
                                disabled = form.isDisabled,
                                errorText = form.errors.get("username"),
                                value = form.username,
                                onChange = updateUsername _)(),
                   MuiRaisedButton(`type` = "submit",
                                   primary = true,
                                   label = "Create",
                                   disabled = form.isDisabled)()))
    }

    def renderHeader(props: Props) = {
      val breadcrumbs = BreadcrumbsComponent(
        props.ctl,
        Seq((props.slug, DashboardRoute.Organization(props.slug)),
            ("Groups", DashboardRoute.OrganizationGroups(props.slug)),
            (props.group, DashboardRoute.OrganizationGroup(props.slug, props.group))))

      <.div(^.key := "header",
            App.cardTitleBlock,
            MuiCardTitle(key = "title")(
              <.div(^.`class` := "row",
                    ^.key := "title",
                    <.div(^.`class` := "col-xs-12", breadcrumbs))))
    }

    def render(props: Props, state: State): ReactElement =
      <.section(
        MuiCard(key = "repos")(
          renderHeader(props),
          <.div(<.h3("Members"), renderMembers(props.slug, props.group, state.members)),
          <.hr,
          renderForm(props, state)))
  }

  private val component = ReactComponentB[Props]("Group")
    .initialState(State(None, Seq.empty, defaultFormState))
    .renderBackend[Backend]
    .componentDidMount($ => $.backend.getGroupWithMembers($.props.slug, $.props.group))
    .build

  def apply(ctl: RouterCtl[DashboardRoute], slug: String, name: String) =
    component(Props(ctl, slug, name))
}
