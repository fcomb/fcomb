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
import io.fcomb.frontend.DashboardRoute
import io.fcomb.frontend.api.{Resource, Rpc, RpcMethod}
import io.fcomb.frontend.components.Helpers._
import io.fcomb.frontend.components.Implicits._
import io.fcomb.json.rpc.Formats._
import io.fcomb.json.models.Formats.decodePaginationData
import io.fcomb.models.PaginationData
import io.fcomb.rpc.{MemberUsernameRequest, OrganizationGroupResponse, UserProfileResponse}
import japgolly.scalajs.react._
import japgolly.scalajs.react.extra.router.RouterCtl
import japgolly.scalajs.react.vdom.prefix_<^._
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue

object GroupComponent {
  final case class Props(ctl: RouterCtl[DashboardRoute], orgName: String, name: String)
  final case class FormState(username: String,
                             errors: Map[String, String],
                             isFormDisabled: Boolean)
  final case class State(group: Option[OrganizationGroupResponse],
                         members: Seq[UserProfileResponse],
                         form: FormState)

  private def defaultFormState =
    FormState("", Map.empty, false)

  final class Backend($ : BackendScope[Props, State]) {
    def getGroup(orgName: String, name: String) =
      Callback.future {
        Rpc
          .call[OrganizationGroupResponse](RpcMethod.GET,
                                           Resource.organizationGroup(orgName, name))
          .map {
            case Xor.Right(group) => $.modState(_.copy(group = Some(group)))
            case Xor.Left(e)      => Callback.warn(e)
          }
      }

    def getMembers(orgName: String, name: String) =
      Callback.future {
        Rpc
          .call[PaginationData[UserProfileResponse]](
            RpcMethod.GET,
            Resource.organizationGroupMembers(orgName, name))
          .map {
            case Xor.Right(pd) => $.modState(_.copy(members = pd.data))
            case Xor.Left(e)   => Callback.warn(e)
          }
      }

    def getGroupWithMembers(orgName: String, name: String): Callback =
      for {
        _ <- getGroup(orgName, name)
        _ <- getMembers(orgName, name)
      } yield ()

    def renderGroup(groupOpt: Option[OrganizationGroupResponse]) =
      groupOpt match {
        case Some(group) =>
          <.div(
            <.h2(s"Group ${group.name}"),
            <.label("Role: ", <.span(group.role.toString()))
          )
        case None => EmptyTag
      }

    def deleteMember(orgName: String, name: String, username: String)(e: ReactEventI) =
      e.preventDefaultCB >>
        Callback.future {
          Rpc
            .call[Unit](RpcMethod.DELETE,
                        Resource.organizationGroupMember(orgName, name, username))
            .map {
              case Xor.Right(_) => getMembers(orgName, name)
              case Xor.Left(e)  => ??? // TODO
            }
        }

    def renderMember(orgName: String, name: String, member: UserProfileResponse) =
      <.tr(<.td(member.username),
           <.td(member.email),
           <.td(
             <.button(^.`type` := "button",
                      ^.onClick ==> deleteMember(orgName, name, member.username),
                      "Delete")))

    def renderMembers(orgName: String, name: String, members: Seq[UserProfileResponse]) =
      if (members.isEmpty) <.span("No members. Create one!")
      else
        <.table(<.thead(<.tr(<.th("Username"), <.th("Email"), <.th())),
                <.tbody(members.map(renderMember(orgName, name, _))))

    def updateFormDisabled(isFormDisabled: Boolean): Callback =
      $.modState(s => s.copy(form = s.form.copy(isFormDisabled = isFormDisabled)))

    def add(props: Props): Callback =
      $.state.flatMap { state =>
        val fs = state.form
        if (fs.isFormDisabled) Callback.empty
        else {
          $.setState(state.copy(form = fs.copy(isFormDisabled = true))) >>
            Callback.future {
              Rpc
                .callWith[MemberUsernameRequest, Unit](
                  RpcMethod.PUT,
                  Resource.organizationGroupMembers(props.orgName, props.name),
                  MemberUsernameRequest(fs.username))
                .map {
                  case Xor.Right(_) =>
                    $.modState(_.copy(form = defaultFormState)) >>
                      getMembers(props.orgName, props.name)
                  case Xor.Left(errs) =>
                    $.setState(state.copy(
                      form = state.form.copy(isFormDisabled = false, errors = foldErrors(errs))))
                }
                .recover {
                  case _ => updateFormDisabled(false)
                }
            }
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
             ^.disabled := form.isFormDisabled,
             <.div(^.display.flex,
                   ^.flexDirection.column,
                   MuiTextField(floatingLabelText = "Username",
                                id = "username",
                                name = "username",
                                disabled = form.isFormDisabled,
                                errorText = form.errors.get("username"),
                                value = form.username,
                                onChange = updateUsername _)(),
                   MuiRaisedButton(`type` = "submit",
                                   primary = true,
                                   label = "Create",
                                   disabled = form.isFormDisabled)()))
    }

    def render(props: Props, state: State) =
      <.section(
        renderGroup(state.group),
        <.div(<.h3("Members"), renderMembers(props.orgName, props.name, state.members)),
        <.hr,
        renderForm(props, state)
      )
  }

  private val component = ReactComponentB[Props]("Group")
    .initialState(State(None, Seq.empty, defaultFormState))
    .renderBackend[Backend]
    .componentDidMount($ => $.backend.getGroupWithMembers($.props.orgName, $.props.name))
    .build

  def apply(ctl: RouterCtl[DashboardRoute], orgName: String, name: String) =
    component(Props(ctl, orgName, name))
}
