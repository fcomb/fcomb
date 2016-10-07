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

import cats.syntax.eq._
import chandu0101.scalajs.react.components.Implicits._
import chandu0101.scalajs.react.components.materialui._
import diode.data.PotMap
import diode.react.ModelProxy
import diode.react.ReactPot._
import io.fcomb.frontend.DashboardRoute
import io.fcomb.frontend.components.LayoutComponent
import io.fcomb.frontend.dispatcher.AppCircuit
import io.fcomb.frontend.styles.App
import io.fcomb.models.OwnerKind
import io.fcomb.models.acl.Action
import io.fcomb.rpc.docker.distribution.RepositoryResponse
import japgolly.scalajs.react._
import japgolly.scalajs.react.extra.router.RouterCtl
import japgolly.scalajs.react.vdom.prefix_<^._
import scalacss.ScalaCssReact._

object RepositoryComponent {
  final case class Props(ctl: RouterCtl[DashboardRoute],
                         tab: RepositoryTab,
                         repositories: ModelProxy[PotMap[String, RepositoryResponse]],
                         slug: String)

  final class Backend($ : BackendScope[Props, Unit]) {
    def onChange(tab: RepositoryTab, e: ReactEventH, el: ReactElement): Callback =
      $.props.flatMap { props =>
        val route = tab match {
          case RepositoryTab.Description => DashboardRoute.Repository(props.slug)
          case RepositoryTab.Tags        => DashboardRoute.RepositoryTags(props.slug)
          case RepositoryTab.Permissions => DashboardRoute.RepositoryPermissions(props.slug)
          case RepositoryTab.Settings    => DashboardRoute.RepositorySettings(props.slug)
        }
        props.ctl.set(route)
      }

    def renderTabs(props: Props, repo: RepositoryResponse, isManageable: Boolean) = {
      val manageTabs =
        if (isManageable)
          Seq(
            MuiTab(key = "permissions", label = "Permissions", value = RepositoryTab.Permissions)(
              RepositoryPermissionsComponent(props.ctl, repo.slug, repo.owner.kind)),
            MuiTab(key = "settings", label = "Settings", value = RepositoryTab.Settings)(
              RepositorySettingsComponent(props.ctl, repo.slug)))
        else Seq.empty

      MuiCardMedia(key = "tabs")(
        MuiTabs[RepositoryTab](value = props.tab, onChange = onChange _)(
          MuiTab(key = "description", label = "Description", value = RepositoryTab.Description)(
            RepositoryDescriptionComponent(repo)),
          MuiTab(key = "tags", label = "Tags", value = RepositoryTab.Tags)(
            RepositoryTagsComponent(repo.slug)),
          manageTabs))
    }

    def setEditRepositoryRoute(props: Props)(e: ReactEventH): Callback =
      props.ctl.set(DashboardRoute.EditRepository(props.slug))

    def breadcrumbLink(ctl: RouterCtl[DashboardRoute], target: DashboardRoute, text: String) =
      <.a(LayoutComponent.linkAsTextStyle,
          ^.href := ctl.urlFor(target).value,
          ctl.setOnLinkClick(target))(text)

    def renderHeader(props: Props, repo: RepositoryResponse, isManageable: Boolean) = {
      val route = repo.owner.kind match {
        case OwnerKind.Organization => DashboardRoute.Organization(repo.namespace)
        case OwnerKind.User =>
          if (AppCircuit.currentUser.exists(_.username == repo.namespace))
            DashboardRoute.Repositories
          else DashboardRoute.User(repo.namespace)
      }
      val buttons: Seq[ReactNode] = if (isManageable) {
        val menuIconBtn =
          MuiIconButton()(Mui.SvgIcons.NavigationMoreVert(color = Mui.Styles.colors.lightBlack)())
        val actions = Seq(
          MuiMenuItem(primaryText = "Edit",
                      key = "edit",
                      onTouchTap = setEditRepositoryRoute(props) _)())
        Seq(MuiIconMenu(iconButtonElement = menuIconBtn)(actions))
      } else Seq.empty
      val breadcrumbs = <.h1(
        App.cardTitle,
        breadcrumbLink(props.ctl, route, repo.namespace),
        " / ",
        breadcrumbLink(props.ctl, DashboardRoute.Repository(repo.slug), repo.name))

      <.div(^.key := "header",
            App.cardTitleBlock,
            MuiCardTitle(key = "title")(
              <.div(^.`class` := "row",
                    ^.key := "title",
                    <.div(^.`class` := "col-xs-11", breadcrumbs),
                    <.div(^.`class` := "col-xs-1", <.div(App.rightActionBlock, buttons)))))
    }

    def render(props: Props): ReactElement = {
      val repository = props.repositories().get(props.slug)
      <.section(repository.renderReady { repo =>
        val isManageable = repo.action === Action.Manage
        MuiCard()(renderHeader(props, repo, isManageable), renderTabs(props, repo, isManageable))
      })
    }
  }

  private val component = ReactComponentB[Props]("Repository").renderBackend[Backend].build

  def apply(ctl: RouterCtl[DashboardRoute],
            tab: RepositoryTab,
            repositories: ModelProxy[PotMap[String, RepositoryResponse]],
            slug: String) =
    component(Props(ctl, tab, repositories, slug))
}
