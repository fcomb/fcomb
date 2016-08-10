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
import io.fcomb.frontend.DashboardRoute
import io.fcomb.frontend.api.{Rpc, RpcMethod, Resource}
import io.fcomb.json.rpc.Formats._
import io.fcomb.json.models.Formats.decodePaginationData
import io.fcomb.models.PaginationData
import io.fcomb.models.acl.Role
import io.fcomb.rpc.{OrganizationGroupResponse, MemberUsernameRequest, UserProfileResponse}
import japgolly.scalajs.react._
import japgolly.scalajs.react.extra.router.RouterCtl
import japgolly.scalajs.react.vdom.prefix_<^._
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue

object GroupComponent {
  final case class Props(ctl: RouterCtl[DashboardRoute], orgName: String, name: String)
  final case class FormState(username: String, isFormDisabled: Boolean)
  final case class State(group: Option[OrganizationGroupResponse],
                         members: Seq[UserProfileResponse],
                         form: FormState)

  private def defaultFormState =
    FormState("", false)

  class Backend($ : BackendScope[Props, State]) {
    def getGroup(name: String) = {
      Callback.future {
        Rpc.call[OrganizationGroupResponse](RpcMethod.GET, Resource.group(name)).map {
          case Xor.Right(group) => $.modState(_.copy(group = Some(group)))
          case Xor.Left(e) =>
            println(e)
            Callback.empty
        }
      }
    }

    def getMembers(name: String) = {
      Callback.future {
        Rpc
          .call[PaginationData[UserProfileResponse]](RpcMethod.GET, Resource.groupMembers(name))
          .map {
            case Xor.Right(pd) => $.modState(_.copy(members = pd.data))
            case Xor.Left(e) =>
              println(e)
              Callback.empty
          }
      }
    }

    def getGroupWithMembers(name: String): Callback = {
      for {
        _ <- getGroup(name)
        _ <- getMembers(name)
      } yield ()
    }

    def renderGroup(groupOpt: Option[OrganizationGroupResponse]) = {
      groupOpt match {
        case Some(group) =>
          <.div(
            <.h2(s"Group ${group.name}"),
            <.label("Role: ", <.span(group.role.toString()))
          )
        case None => EmptyTag
      }
    }

    def deleteMember(name: String, username: String)(e: ReactEventI) = {
      e.preventDefaultCB >>
        Callback.future {
          Rpc.call[Unit](RpcMethod.DELETE, Resource.groupMember(name, username)).map {
            case Xor.Right(_) => getMembers(name)
            case Xor.Left(e)  => ??? // TODO
          }
        }
    }

    def renderMember(name: String, member: UserProfileResponse) = {
      <.tr(<.td(member.username),
           <.td(member.email),
           <.td(
             <.button(^.`type` := "button",
                      ^.onClick ==> deleteMember(name, member.username),
                      "Delete")))
    }

    def renderMembers(name: String, members: Seq[UserProfileResponse]) = {
      if (members.isEmpty) <.span("No members. Create one!")
      else {
        <.div(<.h3("Members"),
              <.table(<.thead(<.tr(<.th("Username"), <.th("Email"), <.th())),
                      <.tbody(members.map(renderMember(name, _)))))
      }
    }

    def updateFormDisabled(isFormDisabled: Boolean): Callback = {
      $.modState(s => s.copy(form = s.form.copy(isFormDisabled = isFormDisabled)))
    }

    def add(props: Props): Callback = {
      $.state.flatMap { state =>
        val fs = state.form
        if (fs.isFormDisabled) Callback.empty
        else {
          $.setState(state.copy(form = fs.copy(isFormDisabled = true))) >>
            Callback.future {
              Rpc
                .callWith[MemberUsernameRequest, Unit](RpcMethod.PUT,
                                                       Resource.groupMembers(props.name),
                                                       MemberUsernameRequest(fs.username))
                .map {
                  case Xor.Right(_) =>
                    $.modState(_.copy(form = defaultFormState)) >>
                      getMembers(props.name)
                  case Xor.Left(e) =>
                    // TODO
                    updateFormDisabled(false)
                }
                .recover {
                  case _ => updateFormDisabled(false)
                }
            }
        }
      }
    }

    def handleOnSubmit(props: Props)(e: ReactEventH): Callback = {
      e.preventDefaultCB >> add(props)
    }

    def updateUsername(e: ReactEventI): Callback = {
      val value = e.target.value
      $.modState(s => s.copy(form = s.form.copy(username = value)))
    }

    def renderForm(props: Props, state: State) = {
      <.form(^.onSubmit ==> handleOnSubmit(props),
             ^.disabled := state.form.isFormDisabled,
             <.input.text(^.id := "username",
                          ^.name := "username",
                          ^.autoFocus := true,
                          ^.required := true,
                          ^.tabIndex := 1,
                          ^.placeholder := "Username",
                          ^.value := state.form.username,
                          ^.onChange ==> updateUsername),
             <.input.submit(^.tabIndex := 2, ^.value := "Add"))
    }

    def render(props: Props, state: State) = {
      <.section(
        renderGroup(state.group),
        renderMembers(props.name, state.members),
        <.hr,
        renderForm(props, state)
      )
    }
  }

  private val component = ReactComponentB[Props]("Group")
    .initialState(State(None, Seq.empty, defaultFormState))
    .renderBackend[Backend]
    .componentWillMount($ => $.backend.getGroupWithMembers($.props.name))
    .build

  def apply(ctl: RouterCtl[DashboardRoute], orgName: String, name: String) =
    component(Props(ctl, orgName, name))
}
